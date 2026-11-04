package com.sequan.escrow.application;

import com.sequan.common.money.MoneyUtils;
import com.sequan.escrow.api.EscrowDtos;
import com.sequan.escrow.domain.ApprovalStatus;
import com.sequan.escrow.domain.EscrowConditionEntity;
import com.sequan.escrow.domain.EscrowEntity;
import com.sequan.escrow.domain.EscrowParticipantEntity;
import com.sequan.escrow.domain.EscrowStatus;
import com.sequan.escrow.domain.ParticipantRole;
import com.sequan.escrow.domain.ParticipationStatus;
import com.sequan.escrow.domain.ShareCalculator;
import com.sequan.payment.domain.ReleaseRepository;
import com.sequan.security.UserPrincipal;
import com.sequan.user.domain.UserEntity;
import com.sequan.user.domain.UserRepository;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class EscrowMapper {

	private final UserRepository userRepository;
	private final ReleaseRepository releaseRepository;

	public EscrowMapper(UserRepository userRepository, ReleaseRepository releaseRepository) {
		this.userRepository = userRepository;
		this.releaseRepository = releaseRepository;
	}

	public EscrowDtos.EscrowResponse toResponse(
			EscrowEntity escrow,
			List<EscrowParticipantEntity> participants,
			List<EscrowConditionEntity> conditions,
			UserPrincipal viewer
	) {
		Map<UUID, UserEntity> users = userRepository.findAllById(
				participants.stream().map(EscrowParticipantEntity::getUserId).toList()
		).stream().collect(Collectors.toMap(UserEntity::getId, Function.identity()));

		List<EscrowDtos.ParticipantResponse> participantResponses = participants.stream()
				.map(p -> {
					UserEntity user = users.get(p.getUserId());
					boolean isPayee = p.getRole() == ParticipantRole.PROVIDER
							|| p.getRole() == ParticipantRole.BENEFICIARY;
					String entitledAmount = null;
					String remainingShare = null;
					if (isPayee) {
						long entitled = ShareCalculator.entitledAmount(p, escrow.getAmountTotal(), participants);
						long allocated = releaseRepository.sumAllocatedAmountForBeneficiary(escrow.getId(), p.getId());
						long remaining = Math.max(0, entitled - allocated);
						// Cannot release more than escrow still holds.
						remaining = Math.min(remaining, escrow.getAmountAvailable());
						entitledAmount = MoneyUtils.toDecimalString(entitled, escrow.getCurrency());
						remainingShare = MoneyUtils.toDecimalString(remaining, escrow.getCurrency());
					}
					return new EscrowDtos.ParticipantResponse(
							p.getId(),
							p.getUserId(),
							user != null ? user.getEmail() : p.getInvitedEmail(),
							user != null ? user.getFullName() : null,
							p.getRole().name(),
							p.getShareType().name(),
							p.getShareValue(),
							entitledAmount,
							remainingShare,
							p.getParticipationStatus().name(),
							p.getApprovalStatus().name(),
							p.isCanApproveRelease()
					);
				})
				.toList();

		List<EscrowDtos.ConditionResponse> conditionResponses = conditions.stream()
				.map(c -> new EscrowDtos.ConditionResponse(
						c.getId(),
						c.getTitle(),
						c.getDescription(),
						c.getSortOrder(),
						c.getStatus().name(),
						c.isRequired()
				))
				.toList();

		return new EscrowDtos.EscrowResponse(
				escrow.getId(),
				escrow.getTitle(),
				escrow.getDescription(),
				escrow.getCurrency(),
				MoneyUtils.toDecimalString(escrow.getAmountTotal(), escrow.getCurrency()),
				MoneyUtils.toDecimalString(escrow.getAmountFunded(), escrow.getCurrency()),
				MoneyUtils.toDecimalString(escrow.getAmountReleased(), escrow.getCurrency()),
				MoneyUtils.toDecimalString(escrow.getAmountRefunded(), escrow.getCurrency()),
				MoneyUtils.toDecimalString(escrow.getAmountAvailable(), escrow.getCurrency()),
				escrow.getStatus().name(),
				escrow.getFundingDeadline(),
				escrow.getCompletionDeadline(),
				escrow.getCreatedBy(),
				participantResponses,
				conditionResponses,
				availableActions(escrow, participants, viewer),
				escrow.getCreatedAt(),
				escrow.getUpdatedAt()
		);
	}

	private List<String> availableActions(
			EscrowEntity escrow,
			List<EscrowParticipantEntity> participants,
			UserPrincipal viewer
	) {
		List<String> actions = new ArrayList<>();
		EscrowParticipantEntity me = participants.stream()
				.filter(p -> p.getUserId().equals(viewer.getId()))
				.findFirst()
				.orElse(null);
		boolean isClient = me != null && me.getRole() == ParticipantRole.CLIENT;
		boolean isAdmin = viewer.getGlobalRole().name().equals("ADMIN");

		if (escrow.getStatus() == EscrowStatus.DRAFT && (isClient || isAdmin)) {
			actions.add("PUBLISH");
			actions.add("UPDATE");
			actions.add("CANCEL");
		}
		if (me != null && me.getParticipationStatus() == ParticipationStatus.PENDING
				&& escrow.getStatus() == EscrowStatus.PENDING_PARTICIPANTS) {
			actions.add("ACCEPT");
			actions.add("DECLINE");
		}
		if (escrow.getStatus() == EscrowStatus.PENDING_FUNDING && (isClient || isAdmin)) {
			actions.add("FUND");
			actions.add("CANCEL");
		}
		boolean isProvider = me != null && me.getRole() == ParticipantRole.PROVIDER;
		boolean isApprover = me != null && me.getRole() == ParticipantRole.APPROVER;
		if ((escrow.getStatus() == EscrowStatus.FUNDED || escrow.getStatus() == EscrowStatus.IN_PROGRESS)
				&& (isClient || isAdmin || isProvider)) {
			// Only the provider starts work; client already funded.
			if (escrow.getStatus() == EscrowStatus.FUNDED && (isProvider || isAdmin)) {
				actions.add("START");
			}
			// Provider requests payout; client approves/rejects afterward.
			if (escrow.getStatus() == EscrowStatus.IN_PROGRESS && (isProvider || isAdmin)) {
				actions.add("REQUEST_RELEASE");
			}
			if (isClient || isAdmin) {
				actions.add("REFUND");
			}
			if (me != null) {
				actions.add("DISPUTE");
			}
		}
		// Client/approver verify conditions; provider does not self-certify release conditions.
		if ((escrow.getStatus() == EscrowStatus.FUNDED || escrow.getStatus() == EscrowStatus.IN_PROGRESS)
				&& (isClient || isApprover || isAdmin)) {
			actions.add("SATISFY_CONDITION");
		}
		if (escrow.getStatus() == EscrowStatus.AWAITING_RELEASE || escrow.getStatus() == EscrowStatus.PARTIALLY_RELEASED) {
			// Maker-checker: approve first, then release funds (never both at once).
			boolean canApprove = me != null
					&& me.isCanApproveRelease()
					&& me.getApprovalStatus() == ApprovalStatus.PENDING
					&& (me.getRole() == ParticipantRole.CLIENT || me.getRole() == ParticipantRole.APPROVER);
			if (canApprove) {
				actions.add("APPROVE_RELEASE");
				actions.add("REJECT_RELEASE");
			}
			boolean releaseApproved = releaseApprovalsComplete(participants);
			if ((isClient || isAdmin) && releaseApproved) {
				actions.add("RELEASE");
			}
			if (isClient || isAdmin) {
				actions.add("REFUND");
			}
			if (me != null) {
				actions.add("DISPUTE");
			}
		}
		if (escrow.getStatus() == EscrowStatus.DISPUTED && (isClient || isAdmin
				|| (me != null && me.getRole() == ParticipantRole.ARBITRATOR))) {
			actions.add("RESOLVE_DISPUTE_RELEASE");
			actions.add("RESOLVE_DISPUTE_REFUND");
			actions.add("RESOLVE_DISPUTE_CANCEL");
			if (isClient || isAdmin) {
				actions.add("RELEASE");
				actions.add("REFUND");
			}
		}
		return actions.stream().distinct().toList();
	}

	/** True when every client/approver approver has approved the release request. */
	private boolean releaseApprovalsComplete(List<EscrowParticipantEntity> participants) {
		List<EscrowParticipantEntity> approvers = participants.stream()
				.filter(p -> p.isCanApproveRelease()
						&& (p.getRole() == ParticipantRole.CLIENT || p.getRole() == ParticipantRole.APPROVER))
				.toList();
		if (approvers.isEmpty()) {
			return true;
		}
		return approvers.stream().allMatch(a -> a.getApprovalStatus() == ApprovalStatus.APPROVED);
	}
}
