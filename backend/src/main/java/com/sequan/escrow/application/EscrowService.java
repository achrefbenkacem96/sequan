package com.sequan.escrow.application;

import com.sequan.audit.AuditService;
import com.sequan.common.error.DomainException;
import com.sequan.common.error.ErrorCode;
import com.sequan.common.money.MoneyUtils;
import com.sequan.escrow.api.EscrowDtos;
import com.sequan.escrow.domain.ApprovalStatus;
import com.sequan.escrow.domain.ConditionStatus;
import com.sequan.escrow.domain.DisputeEntity;
import com.sequan.escrow.domain.DisputeRepository;
import com.sequan.escrow.domain.DisputeStatus;
import com.sequan.escrow.domain.EscrowConditionEntity;
import com.sequan.escrow.domain.EscrowConditionRepository;
import com.sequan.escrow.domain.EscrowEntity;
import com.sequan.escrow.domain.EscrowParticipantEntity;
import com.sequan.escrow.domain.EscrowParticipantRepository;
import com.sequan.escrow.domain.EscrowRepository;
import com.sequan.escrow.domain.EscrowStateMachine;
import com.sequan.escrow.domain.EscrowStatus;
import com.sequan.escrow.domain.ParticipantRole;
import com.sequan.escrow.domain.ParticipationStatus;
import com.sequan.escrow.domain.ShareCalculator;
import com.sequan.escrow.domain.ShareType;
import com.sequan.notification.NotificationService;
import com.sequan.security.UserPrincipal;
import com.sequan.user.domain.UserEntity;
import com.sequan.user.domain.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class EscrowService {

	private final EscrowRepository escrowRepository;
	private final EscrowParticipantRepository participantRepository;
	private final EscrowConditionRepository conditionRepository;
	private final DisputeRepository disputeRepository;
	private final UserRepository userRepository;
	private final EscrowStateMachine stateMachine;
	private final EscrowAccessService accessService;
	private final EscrowMapper mapper;
	private final AuditService auditService;
	private final NotificationService notificationService;

	public EscrowService(
			EscrowRepository escrowRepository,
			EscrowParticipantRepository participantRepository,
			EscrowConditionRepository conditionRepository,
			DisputeRepository disputeRepository,
			UserRepository userRepository,
			EscrowStateMachine stateMachine,
			EscrowAccessService accessService,
			EscrowMapper mapper,
			AuditService auditService,
			NotificationService notificationService
	) {
		this.escrowRepository = escrowRepository;
		this.participantRepository = participantRepository;
		this.conditionRepository = conditionRepository;
		this.disputeRepository = disputeRepository;
		this.userRepository = userRepository;
		this.stateMachine = stateMachine;
		this.accessService = accessService;
		this.mapper = mapper;
		this.auditService = auditService;
		this.notificationService = notificationService;
	}

	@Transactional
	public EscrowDtos.EscrowResponse create(UserPrincipal actor, EscrowDtos.CreateEscrowRequest request) {
		long amount = MoneyUtils.toMinorUnits(request.amount(), request.currency());
		EscrowEntity escrow = new EscrowEntity(
				UUID.randomUUID(),
				request.title(),
				request.description(),
				request.currency(),
				amount,
				actor.getId(),
				request.fundingDeadline(),
				request.completionDeadline()
		);
		escrowRepository.save(escrow);

		List<EscrowDtos.ParticipantRequest> participants = request.participants() == null
				? List.of()
				: request.participants();
		boolean hasClient = participants.stream().anyMatch(p -> "CLIENT".equalsIgnoreCase(p.role()));
		if (!hasClient) {
			addParticipant(escrow, actor.getId(), ParticipantRole.CLIENT, ShareType.PERCENT_BPS, 0, true, actor.getUsername());
		}
		for (EscrowDtos.ParticipantRequest p : participants) {
			ParticipantRole role = ParticipantRole.valueOf(p.role().toUpperCase());
			boolean isPayee = role == ParticipantRole.PROVIDER || role == ParticipantRole.BENEFICIARY;
			addParticipant(
					escrow,
					p.userId(),
					role,
					ShareType.valueOf(p.shareType().toUpperCase()),
					isPayee ? p.shareValue() : 0,
					false,
					null
			);
		}
		validateSharesSoft(escrow.getAmountTotal(), participantRepository.findByEscrowId(escrow.getId()));

		int order = 0;
		if (request.conditions() != null) {
			for (EscrowDtos.ConditionRequest condition : request.conditions()) {
				conditionRepository.save(new EscrowConditionEntity(
						UUID.randomUUID(),
						escrow.getId(),
						condition.title(),
						condition.description(),
						order++,
						condition.required()
				));
			}
		}

		auditService.record(escrow.getId(), actor.getId(), "ESCROW_CREATED", "Escrow", escrow.getId(),
				Map.of("amount", amount, "currency", escrow.getCurrency()));
		return get(escrow.getId(), actor);
	}

	@Transactional(readOnly = true)
	public List<EscrowDtos.EscrowResponse> list(UserPrincipal actor) {
		return escrowRepository.findAccessibleByUser(actor.getId()).stream()
				.map(e -> mapper.toResponse(
						e,
						participantRepository.findByEscrowId(e.getId()),
						conditionRepository.findByEscrowIdOrderBySortOrderAsc(e.getId()),
						actor
				))
				.toList();
	}

	@Transactional(readOnly = true)
	public EscrowDtos.EscrowResponse get(UUID escrowId, UserPrincipal actor) {
		EscrowEntity escrow = accessService.requireAccessible(escrowId, actor);
		return mapper.toResponse(
				escrow,
				participantRepository.findByEscrowId(escrowId),
				conditionRepository.findByEscrowIdOrderBySortOrderAsc(escrowId),
				actor
		);
	}

	@Transactional
	public EscrowDtos.EscrowResponse update(UUID escrowId, UserPrincipal actor, EscrowDtos.UpdateEscrowRequest request) {
		EscrowEntity escrow = accessService.requireAccessible(escrowId, actor);
		accessService.requireRole(escrowId, actor, ParticipantRole.CLIENT);
		escrow.updateDraft(request.title(), request.description(), request.fundingDeadline(), request.completionDeadline());
		auditService.record(escrowId, actor.getId(), "ESCROW_UPDATED", "Escrow", escrowId, null);
		return get(escrowId, actor);
	}

	@Transactional
	public EscrowDtos.EscrowResponse publish(UUID escrowId, UserPrincipal actor) {
		EscrowEntity escrow = accessService.requireAccessible(escrowId, actor);
		accessService.requireRole(escrowId, actor, ParticipantRole.CLIENT);
		escrow.assertStatus(EscrowStatus.DRAFT);
		List<EscrowParticipantEntity> participants = participantRepository.findByEscrowId(escrowId);
		validateShares(escrow.getAmountTotal(), participants);
		escrow.transitionTo(EscrowStatus.PENDING_PARTICIPANTS, stateMachine);
		maybeAdvanceAfterAcceptances(escrow, participants);
		for (EscrowParticipantEntity p : participants) {
			if (p.getParticipationStatus() == ParticipationStatus.PENDING) {
				notificationService.notify(p.getUserId(), escrowId, "INVITE",
						"Escrow invitation", "You have been invited to participate in: " + escrow.getTitle());
			}
		}
		auditService.record(escrowId, actor.getId(), "ESCROW_PUBLISHED", "Escrow", escrowId, null);
		return get(escrowId, actor);
	}

	@Transactional
	public EscrowDtos.EscrowResponse cancel(UUID escrowId, UserPrincipal actor) {
		EscrowEntity escrow = accessService.requireAccessible(escrowId, actor);
		accessService.requireRole(escrowId, actor, ParticipantRole.CLIENT);
		escrow.assertOneOf(EscrowStatus.DRAFT, EscrowStatus.PENDING_PARTICIPANTS, EscrowStatus.PENDING_FUNDING);
		escrow.transitionTo(EscrowStatus.CANCELLED, stateMachine);
		auditService.record(escrowId, actor.getId(), "ESCROW_CANCELLED", "Escrow", escrowId, null);
		return get(escrowId, actor);
	}

	@Transactional
	public EscrowDtos.EscrowResponse addParticipant(UUID escrowId, UserPrincipal actor, EscrowDtos.AddParticipantRequest request) {
		EscrowEntity escrow = accessService.requireAccessible(escrowId, actor);
		accessService.requireRole(escrowId, actor, ParticipantRole.CLIENT);
		escrow.assertOneOf(EscrowStatus.DRAFT, EscrowStatus.PENDING_PARTICIPANTS);
		EscrowParticipantEntity participant = addParticipant(
				escrow,
				request.userId(),
				ParticipantRole.valueOf(request.role().toUpperCase()),
				ShareType.valueOf(request.shareType().toUpperCase()),
				request.shareValue(),
				request.canApproveRelease(),
				null
		);
		validateSharesSoft(escrow.getAmountTotal(), participantRepository.findByEscrowId(escrowId));
		auditService.record(escrowId, actor.getId(), "PARTICIPANT_ADDED", "EscrowParticipant", participant.getId(),
				Map.of("userId", request.userId().toString(), "role", request.role()));
		notificationService.notify(request.userId(), escrowId, "INVITE",
				"Escrow invitation", "You have been invited to participate in: " + escrow.getTitle());
		return get(escrowId, actor);
	}

	@Transactional
	public EscrowDtos.EscrowResponse accept(UUID escrowId, UserPrincipal actor) {
		EscrowEntity escrow = accessService.requireAccessible(escrowId, actor);
		escrow.assertOneOf(EscrowStatus.PENDING_PARTICIPANTS);
		EscrowParticipantEntity participant = accessService.requireParticipant(escrowId, actor.getId());
		if (participant.getParticipationStatus() != ParticipationStatus.PENDING) {
			throw new DomainException(ErrorCode.CONFLICT, HttpStatus.CONFLICT, "Participation already decided");
		}
		participant.accept();
		auditService.record(escrowId, actor.getId(), "PARTICIPANT_ACCEPTED", "EscrowParticipant", participant.getId(), null);
		maybeAdvanceAfterAcceptances(escrow, participantRepository.findByEscrowId(escrowId));
		return get(escrowId, actor);
	}

	@Transactional
	public EscrowDtos.EscrowResponse decline(UUID escrowId, UserPrincipal actor) {
		EscrowEntity escrow = accessService.requireAccessible(escrowId, actor);
		escrow.assertOneOf(EscrowStatus.DRAFT, EscrowStatus.PENDING_PARTICIPANTS);
		EscrowParticipantEntity participant = accessService.requireParticipant(escrowId, actor.getId());
		if (participant.getParticipationStatus() != ParticipationStatus.PENDING) {
			throw new DomainException(ErrorCode.CONFLICT, HttpStatus.CONFLICT, "Participation already decided");
		}
		participant.decline();
		auditService.record(escrowId, actor.getId(), "PARTICIPANT_DECLINED", "EscrowParticipant", participant.getId(), null);
		escrow.transitionTo(EscrowStatus.CANCELLED, stateMachine);
		// Cancelled escrows are creator-only afterward — return the final snapshot
		// without re-running requireAccessible (which would fail for the decliner).
		return mapper.toResponse(
				escrow,
				participantRepository.findByEscrowId(escrowId),
				conditionRepository.findByEscrowIdOrderBySortOrderAsc(escrowId),
				actor
		);
	}

	@Transactional
	public EscrowDtos.EscrowResponse start(UUID escrowId, UserPrincipal actor) {
		EscrowEntity escrow = accessService.requireAccessible(escrowId, actor);
		accessService.requireRole(escrowId, actor, ParticipantRole.PROVIDER);
		escrow.assertStatus(EscrowStatus.FUNDED);
		escrow.transitionTo(EscrowStatus.IN_PROGRESS, stateMachine);
		auditService.record(escrowId, actor.getId(), "ESCROW_STARTED", "Escrow", escrowId, null);
		return get(escrowId, actor);
	}

	@Transactional
	public EscrowDtos.EscrowResponse requestRelease(UUID escrowId, UserPrincipal actor) {
		EscrowEntity escrow = accessService.requireAccessible(escrowId, actor);
		accessService.requireRole(escrowId, actor, ParticipantRole.PROVIDER);
		escrow.assertStatus(EscrowStatus.IN_PROGRESS);
		List<EscrowConditionEntity> conditions = conditionRepository.findByEscrowIdOrderBySortOrderAsc(escrowId);
		List<String> pendingRequired = conditions.stream()
				.filter(EscrowConditionEntity::isRequired)
				.filter(c -> c.getStatus() != ConditionStatus.SATISFIED && c.getStatus() != ConditionStatus.WAIVED)
				.map(EscrowConditionEntity::getTitle)
				.toList();
		if (!pendingRequired.isEmpty()) {
			String names = String.join(", ", pendingRequired);
			throw new DomainException(ErrorCode.ESCROW_INVALID_STATE, HttpStatus.CONFLICT,
					"Mark all required conditions as satisfied before requesting release. Still pending: " + names);
		}
		participantRepository.findByEscrowId(escrowId).stream()
				.filter(this::canApproveRelease)
				.forEach(EscrowParticipantEntity::resetApproval);
		escrow.transitionTo(EscrowStatus.AWAITING_RELEASE, stateMachine);
		auditService.record(escrowId, actor.getId(), "RELEASE_REQUESTED", "Escrow", escrowId, null);
		return get(escrowId, actor);
	}

	@Transactional
	public EscrowDtos.EscrowResponse approveRelease(UUID escrowId, UserPrincipal actor) {
		EscrowEntity escrow = accessService.requireAccessible(escrowId, actor);
		escrow.assertOneOf(EscrowStatus.AWAITING_RELEASE, EscrowStatus.PARTIALLY_RELEASED);
		EscrowParticipantEntity participant = accessService.requireParticipant(escrowId, actor.getId());
		if (!canApproveRelease(participant)) {
			throw new DomainException(ErrorCode.FORBIDDEN, HttpStatus.FORBIDDEN, "You cannot approve releases");
		}
		participant.approveRelease();
		auditService.record(escrowId, actor.getId(), "RELEASE_APPROVED", "EscrowParticipant", participant.getId(), null);
		return get(escrowId, actor);
	}

	@Transactional
	public EscrowDtos.EscrowResponse rejectRelease(UUID escrowId, UserPrincipal actor) {
		EscrowEntity escrow = accessService.requireAccessible(escrowId, actor);
		escrow.assertStatus(EscrowStatus.AWAITING_RELEASE);
		EscrowParticipantEntity participant = accessService.requireParticipant(escrowId, actor.getId());
		if (!canApproveRelease(participant)) {
			throw new DomainException(ErrorCode.FORBIDDEN, HttpStatus.FORBIDDEN, "You cannot reject releases");
		}
		participant.rejectRelease();
		participantRepository.findByEscrowId(escrowId).stream()
				.filter(this::canApproveRelease)
				.forEach(EscrowParticipantEntity::resetApproval);
		escrow.transitionTo(EscrowStatus.IN_PROGRESS, stateMachine);
		auditService.record(escrowId, actor.getId(), "RELEASE_REJECTED", "EscrowParticipant", participant.getId(), null);
		return get(escrowId, actor);
	}

	@Transactional
	public EscrowDtos.EscrowResponse satisfyCondition(UUID escrowId, UUID conditionId, UserPrincipal actor) {
		EscrowEntity escrow = accessService.requireAccessible(escrowId, actor);
		accessService.requireRole(escrowId, actor, ParticipantRole.CLIENT, ParticipantRole.APPROVER);
		escrow.assertOneOf(EscrowStatus.FUNDED, EscrowStatus.IN_PROGRESS);
		EscrowConditionEntity condition = conditionRepository.findByIdAndEscrowId(conditionId, escrowId)
				.orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, "Condition not found"));
		condition.satisfy();
		auditService.record(escrowId, actor.getId(), "CONDITION_SATISFIED", "EscrowCondition", conditionId, null);
		if (escrow.getStatus() == EscrowStatus.FUNDED) {
			escrow.transitionTo(EscrowStatus.IN_PROGRESS, stateMachine);
		}
		return get(escrowId, actor);
	}

	@Transactional
	public EscrowDtos.EscrowResponse openDispute(UUID escrowId, UserPrincipal actor, EscrowDtos.DisputeRequest request) {
		EscrowEntity escrow = accessService.requireAccessible(escrowId, actor);
		accessService.requireParticipant(escrowId, actor.getId());
		escrow.assertOneOf(EscrowStatus.FUNDED, EscrowStatus.IN_PROGRESS, EscrowStatus.AWAITING_RELEASE, EscrowStatus.PARTIALLY_RELEASED);
		boolean hasOpenDispute = disputeRepository.findByEscrowIdOrderByCreatedAtDesc(escrowId).stream()
				.anyMatch(d -> d.getStatus() == DisputeStatus.OPEN);
		if (hasOpenDispute) {
			throw new DomainException(ErrorCode.CONFLICT, HttpStatus.CONFLICT, "An open dispute already exists");
		}
		DisputeEntity dispute = new DisputeEntity(UUID.randomUUID(), escrowId, actor.getId(), request.reason());
		disputeRepository.save(dispute);
		escrow.transitionTo(EscrowStatus.DISPUTED, stateMachine);
		auditService.record(escrowId, actor.getId(), "DISPUTE_OPENED", "Dispute", dispute.getId(),
				Map.of("reason", request.reason()));
		return get(escrowId, actor);
	}

	@Transactional(readOnly = true)
	public List<EscrowDtos.TimelineEventResponse> timeline(UUID escrowId, UserPrincipal actor) {
		accessService.requireAccessible(escrowId, actor);
		return auditService.timeline(escrowId).stream()
				.map(a -> new EscrowDtos.TimelineEventResponse(
						a.getId(),
						a.getAction(),
						a.getActorUserId(),
						a.getEntityType(),
						a.getEntityId(),
						a.getMetadata(),
						a.getCreatedAt()
				))
				.toList();
	}

	public void assertReleaseApprovals(UUID escrowId) {
		List<EscrowParticipantEntity> approvers = participantRepository.findByEscrowId(escrowId).stream()
				.filter(this::canApproveRelease)
				.toList();
		if (approvers.isEmpty()) {
			return;
		}
		boolean allApproved = approvers.stream().allMatch(a -> a.getApprovalStatus() == ApprovalStatus.APPROVED);
		if (!allApproved) {
			throw new DomainException(ErrorCode.ESCROW_INVALID_STATE, HttpStatus.CONFLICT,
					"Release requires approval from all designated approvers");
		}
	}

	private EscrowParticipantEntity addParticipant(
			EscrowEntity escrow,
			UUID userId,
			ParticipantRole role,
			ShareType shareType,
			long shareValue,
			boolean canApproveRelease,
			String invitedEmail
	) {
		UserEntity user = userRepository.findById(userId)
				.orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, "Participant user not found"));
		ParticipationStatus status = userId.equals(escrow.getCreatedBy()) || role == ParticipantRole.CLIENT
				? ParticipationStatus.ACCEPTED
				: ParticipationStatus.PENDING;
		// Only client/approver may approve releases — not the provider (self-approval).
		boolean approveRelease = role == ParticipantRole.CLIENT
				|| role == ParticipantRole.APPROVER
				|| (canApproveRelease
					&& role != ParticipantRole.PROVIDER
					&& role != ParticipantRole.BENEFICIARY);
		EscrowParticipantEntity participant = new EscrowParticipantEntity(
				UUID.randomUUID(),
				escrow.getId(),
				userId,
				role,
				shareType,
				shareValue,
				status,
				approveRelease,
				invitedEmail != null ? invitedEmail : user.getEmail()
		);
		return participantRepository.save(participant);
	}

	/** Providers/beneficiaries must not approve their own payout request. */
	private boolean canApproveRelease(EscrowParticipantEntity participant) {
		if (!participant.isCanApproveRelease()) {
			return false;
		}
		ParticipantRole role = participant.getRole();
		return role == ParticipantRole.CLIENT || role == ParticipantRole.APPROVER;
	}

	private void maybeAdvanceAfterAcceptances(EscrowEntity escrow, List<EscrowParticipantEntity> participants) {
		if (escrow.getStatus() != EscrowStatus.PENDING_PARTICIPANTS) {
			return;
		}
		boolean allAccepted = participants.stream()
				.allMatch(p -> p.getParticipationStatus() == ParticipationStatus.ACCEPTED);
		if (allAccepted) {
			escrow.transitionTo(EscrowStatus.PENDING_FUNDING, stateMachine);
			auditService.record(escrow.getId(), null, "ESCROW_READY_FOR_FUNDING", "Escrow", escrow.getId(), null);
		}
	}

	private void validateSharesSoft(long amountTotal, List<EscrowParticipantEntity> participants) {
		List<EscrowParticipantEntity> payees = ShareCalculator.payeesOf(participants);
		if (payees.isEmpty()) {
			return;
		}
		long fixed = 0;
		long bps = 0;
		Set<ShareType> types = EnumSet.noneOf(ShareType.class);
		for (EscrowParticipantEntity p : payees) {
			types.add(p.getShareType());
			if (p.getShareType() == ShareType.FIXED_MINOR) {
				fixed += p.getShareValue();
			} else {
				bps += p.getShareValue();
			}
		}
		if (types.size() > 1) {
			throw new DomainException(ErrorCode.PARTICIPANT_INVALID, HttpStatus.BAD_REQUEST,
					"All payee shares must use the same share type");
		}
		if (fixed > amountTotal) {
			throw new DomainException(ErrorCode.PARTICIPANT_INVALID, HttpStatus.BAD_REQUEST,
					"Fixed shares exceed escrow amount");
		}
		if (bps > 10000) {
			throw new DomainException(ErrorCode.PARTICIPANT_INVALID, HttpStatus.BAD_REQUEST,
					"Percent shares cannot exceed 10000 basis points");
		}
	}

	private void validateShares(long amountTotal, List<EscrowParticipantEntity> participants) {
		List<EscrowParticipantEntity> payees = ShareCalculator.payeesOf(participants);
		if (payees.isEmpty()) {
			throw new DomainException(ErrorCode.PARTICIPANT_INVALID, HttpStatus.BAD_REQUEST,
					"Escrow must include at least one provider or beneficiary payee");
		}
		long fixed = 0;
		long bps = 0;
		Set<ShareType> types = EnumSet.noneOf(ShareType.class);
		for (EscrowParticipantEntity p : payees) {
			types.add(p.getShareType());
			if (p.getShareType() == ShareType.FIXED_MINOR) {
				fixed += p.getShareValue();
			} else {
				bps += p.getShareValue();
			}
		}
		if (types.size() > 1) {
			throw new DomainException(ErrorCode.PARTICIPANT_INVALID, HttpStatus.BAD_REQUEST,
					"All payee shares must use the same share type");
		}
		if (types.contains(ShareType.FIXED_MINOR) && fixed != amountTotal) {
			throw new DomainException(ErrorCode.PARTICIPANT_INVALID, HttpStatus.BAD_REQUEST,
					"Fixed shares must sum exactly to the escrow amount");
		}
		if (types.contains(ShareType.PERCENT_BPS) && bps != 10000) {
			throw new DomainException(ErrorCode.PARTICIPANT_INVALID, HttpStatus.BAD_REQUEST,
					"Percent shares must sum to 10000 basis points");
		}
	}

	@Transactional
	public DisputeEntity resolveDispute(UUID escrowId, UserPrincipal actor, EscrowDtos.ResolveDisputeRequest request) {
		EscrowEntity escrow = accessService.requireAccessible(escrowId, actor);
		accessService.requireRole(escrowId, actor, ParticipantRole.ARBITRATOR, ParticipantRole.CLIENT);
		escrow.assertStatus(EscrowStatus.DISPUTED);
		DisputeEntity dispute = disputeRepository.findByEscrowIdOrderByCreatedAtDesc(escrowId).stream()
				.filter(d -> d.getStatus() == DisputeStatus.OPEN)
				.findFirst()
				.orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, "Open dispute not found"));
		DisputeStatus status = switch (request.resolution().toUpperCase()) {
			case "RELEASE" -> DisputeStatus.RESOLVED_RELEASE;
			case "REFUND" -> DisputeStatus.RESOLVED_REFUND;
			case "CANCEL" -> DisputeStatus.RESOLVED_CANCEL;
			default -> throw new DomainException(ErrorCode.VALIDATION_ERROR, HttpStatus.BAD_REQUEST, "Invalid resolution");
		};
		if ((status == DisputeStatus.RESOLVED_REFUND || status == DisputeStatus.RESOLVED_CANCEL)
				&& escrow.getAmountAvailable() > 0) {
			throw new DomainException(ErrorCode.ESCROW_INVALID_STATE, HttpStatus.CONFLICT,
					"Return or release available funds before resolving the dispute as "
							+ status.name().replace("RESOLVED_", "").toLowerCase());
		}
		dispute.resolve(status, actor.getId(), request.notes());
		if (status == DisputeStatus.RESOLVED_RELEASE) {
			participantRepository.findByEscrowId(escrowId).stream()
					.filter(this::canApproveRelease)
					.forEach(EscrowParticipantEntity::resetApproval);
			escrow.transitionTo(EscrowStatus.AWAITING_RELEASE, stateMachine);
		} else if (status == DisputeStatus.RESOLVED_REFUND) {
			escrow.transitionTo(EscrowStatus.REFUNDED, stateMachine);
		} else {
			escrow.transitionTo(EscrowStatus.CANCELLED, stateMachine);
		}
		auditService.record(escrowId, actor.getId(), "DISPUTE_RESOLVED", "Dispute", dispute.getId(),
				Map.of("resolution", status.name()));
		return dispute;
	}

	@Transactional
	public int expireOverdueFunding() {
		List<EscrowEntity> overdue = escrowRepository.findExpiredPendingFunding(java.time.Instant.now());
		int count = 0;
		for (EscrowEntity escrow : overdue) {
			if (!stateMachine.canTransition(escrow.getStatus(), EscrowStatus.EXPIRED)) {
				continue;
			}
			escrow.transitionTo(EscrowStatus.EXPIRED, stateMachine);
			auditService.record(escrow.getId(), null, "ESCROW_EXPIRED", "Escrow", escrow.getId(),
					Map.of("reason", "funding_deadline"));
			count++;
		}
		return count;
	}

	/**
	 * Workflow next-steps that belong in "Needs attention".
	 * Optional escapes (dispute/refund/cancel) alone do not count.
	 */
	private static final List<String> ATTENTION_ACTION_PRIORITY = List.of(
			"APPROVE_RELEASE",
			"RELEASE",
			"REQUEST_RELEASE",
			"START",
			"FUND",
			"SATISFY_CONDITION",
			"ACCEPT",
			"PUBLISH",
			"RESOLVE_DISPUTE_RELEASE",
			"RESOLVE_DISPUTE_REFUND",
			"RESOLVE_DISPUTE_CANCEL",
			"REJECT_RELEASE"
	);

	public Map<String, Object> dashboardStats(UserPrincipal actor) {
		List<EscrowEntity> escrows = escrowRepository.findAccessibleByUser(actor.getId());
		Map<String, Long> byStatus = new HashMap<>();
		long totalAvailable = 0;
		List<Map<String, Object>> actionRequired = new ArrayList<>();
		for (EscrowEntity e : escrows) {
			byStatus.merge(e.getStatus().name(), 1L, Long::sum);
			totalAvailable += e.getAmountAvailable();
			EscrowDtos.EscrowResponse response = mapper.toResponse(
					e,
					participantRepository.findByEscrowId(e.getId()),
					conditionRepository.findByEscrowIdOrderBySortOrderAsc(e.getId()),
					actor
			);
			String primary = primaryAttentionAction(response.availableActions());
			if (primary != null) {
				actionRequired.add(Map.of(
						"id", e.getId().toString(),
						"title", e.getTitle(),
						"status", e.getStatus().name(),
						"primaryAction", primary,
						"actions", response.availableActions()
				));
			}
		}
		actionRequired.sort((a, b) -> {
			int ia = ATTENTION_ACTION_PRIORITY.indexOf(String.valueOf(a.get("primaryAction")));
			int ib = ATTENTION_ACTION_PRIORITY.indexOf(String.valueOf(b.get("primaryAction")));
			return Integer.compare(ia < 0 ? 99 : ia, ib < 0 ? 99 : ib);
		});
		return Map.of(
				"countsByStatus", byStatus,
				"totalAvailable", totalAvailable,
				"actionRequired", actionRequired,
				"totalEscrows", escrows.size()
		);
	}

	private static String primaryAttentionAction(List<String> actions) {
		if (actions == null || actions.isEmpty()) {
			return null;
		}
		for (String preferred : ATTENTION_ACTION_PRIORITY) {
			if (actions.contains(preferred)) {
				return preferred;
			}
		}
		return null;
	}
}
