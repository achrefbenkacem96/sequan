package com.sequan.payment.application;

import com.sequan.audit.AuditService;
import com.sequan.common.error.DomainException;
import com.sequan.common.error.ErrorCode;
import com.sequan.common.money.MoneyUtils;
import com.sequan.config.SequanProperties;
import com.sequan.escrow.api.EscrowDtos;
import com.sequan.escrow.application.EscrowAccessService;
import com.sequan.escrow.application.EscrowService;
import com.sequan.escrow.domain.EscrowEntity;
import com.sequan.escrow.domain.EscrowParticipantEntity;
import com.sequan.escrow.domain.EscrowParticipantRepository;
import com.sequan.escrow.domain.EscrowRepository;
import com.sequan.escrow.domain.EscrowStateMachine;
import com.sequan.escrow.domain.EscrowStatus;
import com.sequan.escrow.domain.ParticipantRole;
import com.sequan.escrow.domain.ShareCalculator;
import com.sequan.notification.NotificationService;
import com.sequan.payment.StripeGateway;
import com.sequan.payment.domain.LedgerEntryEntity;
import com.sequan.payment.domain.LedgerEntryRepository;
import com.sequan.payment.domain.LedgerEntryType;
import com.sequan.payment.domain.PaymentEntity;
import com.sequan.payment.domain.PaymentRepository;
import com.sequan.payment.domain.PaymentStatus;
import com.sequan.payment.domain.RefundEntity;
import com.sequan.payment.domain.RefundRepository;
import com.sequan.payment.domain.ReleaseEntity;
import com.sequan.payment.domain.ReleaseRepository;
import com.sequan.security.UserPrincipal;
import com.sequan.user.domain.UserEntity;
import com.sequan.user.domain.UserRepository;
import jakarta.persistence.EntityManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class PaymentService {

	private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

	private final EscrowAccessService accessService;
	private final EscrowService escrowService;
	private final EscrowRepository escrowRepository;
	private final EscrowParticipantRepository participantRepository;
	private final EscrowStateMachine stateMachine;
	private final PaymentRepository paymentRepository;
	private final ReleaseRepository releaseRepository;
	private final RefundRepository refundRepository;
	private final LedgerEntryRepository ledgerEntryRepository;
	private final UserRepository userRepository;
	private final StripeGateway stripeGateway;
	private final SequanProperties properties;
	private final AuditService auditService;
	private final NotificationService notificationService;
	private final EntityManager entityManager;
	private final TransactionTemplate requiresNewTx;

	public PaymentService(
			EscrowAccessService accessService,
			EscrowService escrowService,
			EscrowRepository escrowRepository,
			EscrowParticipantRepository participantRepository,
			EscrowStateMachine stateMachine,
			PaymentRepository paymentRepository,
			ReleaseRepository releaseRepository,
			RefundRepository refundRepository,
			LedgerEntryRepository ledgerEntryRepository,
			UserRepository userRepository,
			StripeGateway stripeGateway,
			SequanProperties properties,
			AuditService auditService,
			NotificationService notificationService,
			EntityManager entityManager,
			PlatformTransactionManager transactionManager
	) {
		this.accessService = accessService;
		this.escrowService = escrowService;
		this.escrowRepository = escrowRepository;
		this.participantRepository = participantRepository;
		this.stateMachine = stateMachine;
		this.paymentRepository = paymentRepository;
		this.releaseRepository = releaseRepository;
		this.refundRepository = refundRepository;
		this.ledgerEntryRepository = ledgerEntryRepository;
		this.userRepository = userRepository;
		this.stripeGateway = stripeGateway;
		this.properties = properties;
		this.auditService = auditService;
		this.notificationService = notificationService;
		this.entityManager = entityManager;
		this.requiresNewTx = new TransactionTemplate(transactionManager);
		this.requiresNewTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
	}

	@Transactional
	public EscrowDtos.FundResponse fund(UUID escrowId, UserPrincipal actor, String idempotencyKey, String returnBaseUrl) {
		EscrowEntity escrow = accessService.requireAccessible(escrowId, actor);
		accessService.requireRole(escrowId, actor, ParticipantRole.CLIENT);
		escrow.assertStatus(EscrowStatus.PENDING_FUNDING);
		if (escrow.getFundingDeadline() != null && escrow.getFundingDeadline().isBefore(java.time.Instant.now())) {
			escrow.transitionTo(EscrowStatus.EXPIRED, stateMachine);
			auditService.record(escrowId, actor.getId(), "ESCROW_EXPIRED", "Escrow", escrowId,
					Map.of("reason", "funding_deadline"));
			throw new DomainException(ErrorCode.ESCROW_INVALID_STATE, HttpStatus.CONFLICT,
					"Funding deadline has passed");
		}

		if (idempotencyKey != null && !idempotencyKey.isBlank()) {
			Optional<PaymentEntity> existing = paymentRepository.findByIdempotencyKey(idempotencyKey);
			if (existing.isPresent()) {
				return ensureFundableCheckout(existing.get(), escrow, actor, returnBaseUrl);
			}
		}

		Optional<PaymentEntity> openPayment = paymentRepository
				.findFirstByEscrowIdAndStatusOrderByCreatedAtDesc(escrowId, PaymentStatus.REQUIRES_ACTION);
		if (openPayment.isEmpty()) {
			openPayment = paymentRepository
					.findFirstByEscrowIdAndStatusOrderByCreatedAtDesc(escrowId, PaymentStatus.PENDING);
		}
		if (openPayment.isPresent()) {
			return ensureFundableCheckout(openPayment.get(), escrow, actor, returnBaseUrl);
		}

		UserEntity payer = userRepository.findById(actor.getId())
				.orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, "User not found"));
		String customerId = ensureStripeCustomer(payer);

		PaymentEntity payment = new PaymentEntity(
				UUID.randomUUID(),
				escrowId,
				actor.getId(),
				escrow.getAmountTotal(),
				escrow.getCurrency(),
				idempotencyKey
		);
		paymentRepository.save(payment);
		return createCheckoutForPayment(payment, escrow, customerId, returnBaseUrl, actor.getId());
	}

	/**
	 * Reuses an open Checkout session when it still has a usable client secret;
	 * otherwise creates a fresh embedded session on the same payment row.
	 */
	private EscrowDtos.FundResponse ensureFundableCheckout(
			PaymentEntity payment,
			EscrowEntity escrow,
			UserPrincipal actor,
			String returnBaseUrl
	) {
		if (payment.getStatus() == PaymentStatus.SUCCEEDED) {
			throw new DomainException(ErrorCode.ESCROW_INVALID_STATE, HttpStatus.CONFLICT,
					"Payment already succeeded for this escrow");
		}
		if (payment.getStripeCheckoutSessionId() != null) {
			try {
				StripeGateway.SessionCreateResult existingSession =
						stripeGateway.retrieveCheckoutSession(payment.getStripeCheckoutSessionId());
				if (existingSession.isReusableHosted()) {
					return new EscrowDtos.FundResponse(
							payment.getId(),
							payment.getStripeCheckoutSessionId(),
							existingSession.clientSecret(),
							stripeGateway.publishableKey(),
							existingSession.checkoutUrl()
					);
				}
			} catch (RuntimeException ignored) {
				// Expired or from another Stripe account — recreate below.
			}
		}

		UserEntity payer = userRepository.findById(actor.getId())
				.orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, "User not found"));
		String customerId = ensureStripeCustomer(payer);
		return createCheckoutForPayment(payment, escrow, customerId, returnBaseUrl, actor.getId());
	}

	private String ensureStripeCustomer(UserEntity payer) {
		String customerId = stripeGateway.ensureCustomer(
				payer.getStripeCustomerId(), payer.getEmail(), payer.getFullName());
		if (payer.getStripeCustomerId() == null) {
			payer.setStripeCustomerId(customerId);
		}
		return customerId;
	}

	private EscrowDtos.FundResponse createCheckoutForPayment(
			PaymentEntity payment,
			EscrowEntity escrow,
			String customerId,
			String returnBaseUrl,
			UUID actorId
	) {
		String base = (returnBaseUrl != null && !returnBaseUrl.isBlank())
				? returnBaseUrl.replaceAll("/$", "")
				: properties.app().baseUrl();
		String returnUrl = base + "/escrows/" + escrow.getId()
				+ "?funded=1&session_id={CHECKOUT_SESSION_ID}";
		String cancelUrl = base + "/escrows/" + escrow.getId() + "?funded=0";
		StripeGateway.SessionCreateResult session = stripeGateway.createCheckoutSession(
				escrow.getId(),
				payment.getId(),
				escrow.getAmountTotal(),
				escrow.getCurrency(),
				customerId,
				returnUrl,
				cancelUrl,
				"checkout-" + payment.getId() + "-" + System.currentTimeMillis()
		);
		if (session.checkoutUrl() == null || session.checkoutUrl().isBlank()) {
			payment.markFailed("missing_checkout_url");
			paymentRepository.save(payment);
			throw new DomainException(ErrorCode.STRIPE_ERROR, HttpStatus.BAD_GATEWAY,
					"Stripe Checkout Session was created without a checkout URL");
		}
		payment.attachCheckoutSession(session.sessionId());
		paymentRepository.saveAndFlush(payment);
		auditService.record(escrow.getId(), actorId, "PAYMENT_INITIATED", "Payment", payment.getId(),
				Map.of("sessionId", session.sessionId()));
		return new EscrowDtos.FundResponse(
				payment.getId(),
				session.sessionId(),
				session.clientSecret(),
				stripeGateway.publishableKey(),
				session.checkoutUrl()
		);
	}

	@Transactional
	public void confirmFunding(UUID escrowId, UserPrincipal actor, String sessionId) {
		accessService.requireAccessible(escrowId, actor);
		accessService.requireRole(escrowId, actor, ParticipantRole.CLIENT);
		if (sessionId == null || sessionId.isBlank()) {
			throw new DomainException(ErrorCode.VALIDATION_ERROR, HttpStatus.BAD_REQUEST, "session_id is required");
		}

		StripeGateway.CheckoutSessionDetails session = stripeGateway.retrieveCheckoutSessionDetails(sessionId);
		if (session.escrowId() != null && !session.escrowId().equals(escrowId.toString())) {
			throw new DomainException(ErrorCode.FORBIDDEN, HttpStatus.FORBIDDEN, "Checkout session does not belong to this escrow");
		}

		boolean paid = "paid".equalsIgnoreCase(session.paymentStatus())
				|| "complete".equalsIgnoreCase(session.status());
		if (!paid) {
			throw new DomainException(ErrorCode.PAYMENT_FAILED, HttpStatus.CONFLICT,
					"Checkout session is not paid yet (status=" + session.paymentStatus() + ")");
		}

		PaymentEntity payment = paymentRepository.findByStripeCheckoutSessionId(sessionId)
				.orElseGet(() -> {
					if (session.paymentId() == null || session.paymentId().isBlank()) {
						return null;
					}
					try {
						return paymentRepository.findById(UUID.fromString(session.paymentId())).orElse(null);
					} catch (IllegalArgumentException ex) {
						return null;
					}
				});
		if (payment == null) {
			payment = paymentRepository
					.findFirstByEscrowIdAndStatusOrderByCreatedAtDesc(escrowId, PaymentStatus.REQUIRES_ACTION)
					.or(() -> paymentRepository.findFirstByEscrowIdAndStatusOrderByCreatedAtDesc(escrowId, PaymentStatus.PENDING))
					.orElse(null);
		}
		if (payment == null) {
			// Reconstruct a payment row if Stripe succeeded but local insert was lost/rolled back
			payment = new PaymentEntity(
					session.paymentId() != null && !session.paymentId().isBlank()
							? UUID.fromString(session.paymentId())
							: UUID.randomUUID(),
					escrowId,
					actor.getId(),
					accessService.requireEscrow(escrowId).getAmountTotal(),
					accessService.requireEscrow(escrowId).getCurrency(),
					"confirm-" + sessionId
			);
			paymentRepository.save(payment);
		}
		if (!payment.getEscrowId().equals(escrowId)) {
			throw new DomainException(ErrorCode.FORBIDDEN, HttpStatus.FORBIDDEN, "Payment does not belong to this escrow");
		}
		if (payment.getStripeCheckoutSessionId() == null) {
			payment.attachCheckoutSession(sessionId);
			paymentRepository.save(payment);
		}
		String chargeId = resolveChargeId(session.paymentIntentId(), null);
		markPaymentSucceeded(sessionId, session.paymentIntentId(), chargeId);
	}

	@Transactional
	public void markPaymentSucceeded(String checkoutSessionId, String paymentIntentId, String chargeId) {
		PaymentEntity payment = resolvePayment(checkoutSessionId, paymentIntentId)
				.orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND,
						"Payment not found for Stripe event"));

		String resolvedChargeId = resolveChargeId(paymentIntentId != null ? paymentIntentId : payment.getStripePaymentIntentId(), chargeId);

		if (payment.getStatus() == PaymentStatus.SUCCEEDED) {
			if (resolvedChargeId != null && payment.getStripeChargeId() == null) {
				payment.attachChargeId(resolvedChargeId);
			}
			log.info("Ignoring duplicate payment success for payment {}", payment.getId());
			return;
		}

		EscrowEntity escrow = escrowRepository.findById(payment.getEscrowId())
				.orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, "Escrow not found"));

		if (escrow.getStatus() == EscrowStatus.CANCELLED || escrow.getStatus() == EscrowStatus.EXPIRED) {
			payment.markSucceeded(paymentIntentId, resolvedChargeId);
			autoRefundClosedEscrow(escrow, payment, paymentIntentId);
			return;
		}

		if (escrow.getStatus() != EscrowStatus.PENDING_FUNDING) {
			throw new DomainException(ErrorCode.ESCROW_INVALID_STATE, HttpStatus.CONFLICT,
					"Escrow is not awaiting funding");
		}

		payment.markSucceeded(paymentIntentId, resolvedChargeId);
		escrow.markFunded(payment.getAmount());
		escrow.transitionTo(EscrowStatus.FUNDED, stateMachine);
		ledgerEntryRepository.save(new LedgerEntryEntity(
				UUID.randomUUID(),
				escrow.getId(),
				LedgerEntryType.FUND,
				payment.getAmount(),
				payment.getCurrency(),
				"Payment",
				payment.getId(),
				"Escrow funded via Stripe",
				payment.getPayerUserId()
		));
		auditService.record(escrow.getId(), payment.getPayerUserId(), "PAYMENT_SUCCEEDED", "Payment", payment.getId(),
				Map.of("paymentIntentId", paymentIntentId == null ? "" : paymentIntentId));
		auditService.record(escrow.getId(), null, "ESCROW_FUNDED", "Escrow", escrow.getId(), null);
		participantRepository.findByEscrowId(escrow.getId()).forEach(p ->
				notificationService.notify(p.getUserId(), escrow.getId(), "FUNDED",
						"Escrow funded", "Escrow \"" + escrow.getTitle() + "\" has been funded."));
	}

	private void autoRefundClosedEscrow(EscrowEntity escrow, PaymentEntity payment, String paymentIntentId) {
		String intentId = paymentIntentId != null ? paymentIntentId : payment.getStripePaymentIntentId();
		if (intentId == null || intentId.isBlank()) {
			log.warn("Cannot auto-refund payment {} for closed escrow {}: missing payment intent",
					payment.getId(), escrow.getId());
			auditService.record(escrow.getId(), payment.getPayerUserId(), "PAYMENT_ORPHANED", "Payment", payment.getId(),
					Map.of("escrowStatus", escrow.getStatus().name()));
			return;
		}
		RefundEntity refund = new RefundEntity(
				UUID.randomUUID(),
				escrow.getId(),
				payment.getId(),
				payment.getPayerUserId(),
				payment.getAmount(),
				payment.getCurrency(),
				"auto-refund-" + payment.getId()
		);
		refundRepository.save(refund);
		try {
			String stripeRefundId = stripeGateway.createRefund(intentId, payment.getAmount(), refund.getId(), escrow.getId(),
					"auto-refund-" + payment.getId());
			refund.complete(stripeRefundId);
			auditService.record(escrow.getId(), payment.getPayerUserId(), "PAYMENT_AUTO_REFUNDED", "Refund", refund.getId(),
					Map.of("reason", "escrow_" + escrow.getStatus().name().toLowerCase(), "stripeRefundId", stripeRefundId));
		} catch (RuntimeException ex) {
			refund.fail(ex.getMessage());
			auditService.record(escrow.getId(), payment.getPayerUserId(), "PAYMENT_AUTO_REFUND_FAILED", "Refund",
					refund.getId(), Map.of("error", ex.getMessage() == null ? "" : ex.getMessage()));
			throw ex;
		}
	}

	private Optional<PaymentEntity> resolvePayment(String checkoutSessionId, String paymentIntentId) {
		if (checkoutSessionId != null && !checkoutSessionId.isBlank()) {
			Optional<PaymentEntity> bySession = paymentRepository.findByStripeCheckoutSessionId(checkoutSessionId);
			if (bySession.isPresent()) {
				return bySession;
			}
		}
		if (paymentIntentId != null && !paymentIntentId.isBlank()) {
			return paymentRepository.findByStripePaymentIntentId(paymentIntentId);
		}
		return Optional.empty();
	}

	/**
	 * Prefer an explicit charge id; otherwise look it up from the PaymentIntent.
	 * Lookup failures are logged and treated as missing so callers can still proceed
	 * (e.g. webhook paths) without failing the whole funding event.
	 */
	private String resolveChargeId(String paymentIntentId, String explicitChargeId) {
		if (explicitChargeId != null && !explicitChargeId.isBlank()) {
			return explicitChargeId;
		}
		if (paymentIntentId == null || paymentIntentId.isBlank()) {
			return null;
		}
		try {
			return stripeGateway.retrieveLatestChargeId(paymentIntentId);
		} catch (RuntimeException ex) {
			log.warn("Unable to resolve charge for payment intent {}: {}", paymentIntentId, ex.getMessage());
			return null;
		}
	}

	/**
	 * Backfills and returns the funding charge id used as transfer source_transaction.
	 * Persists in a new transaction so a later Stripe transfer failure does not roll it back.
	 */
	private String ensureFundingChargeId(PaymentEntity fundingPayment) {
		if (fundingPayment.getStripeChargeId() != null && !fundingPayment.getStripeChargeId().isBlank()) {
			return fundingPayment.getStripeChargeId();
		}
		String chargeId = resolveChargeId(fundingPayment.getStripePaymentIntentId(), null);
		if (chargeId == null || chargeId.isBlank()) {
			throw new DomainException(ErrorCode.PAYMENT_FAILED, HttpStatus.CONFLICT,
					"Funding payment is missing a Stripe charge id. Re-confirm funding or wait for "
							+ "payment_intent.succeeded before releasing.");
		}
		UUID paymentId = fundingPayment.getId();
		requiresNewTx.executeWithoutResult(status -> {
			PaymentEntity managed = paymentRepository.findById(paymentId)
					.orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, "Payment not found"));
			managed.attachChargeId(chargeId);
			paymentRepository.save(managed);
		});
		fundingPayment.attachChargeId(chargeId);
		log.info("Backfilled stripe_charge_id {} for payment {}", chargeId, fundingPayment.getId());
		return chargeId;
	}

	@Transactional
	public void markPaymentFailed(String paymentIntentId, String reason) {
		paymentRepository.findByStripePaymentIntentId(paymentIntentId).ifPresent(payment -> {
			if (payment.getStatus() == PaymentStatus.SUCCEEDED) {
				return;
			}
			payment.markFailed(reason);
			auditService.record(payment.getEscrowId(), payment.getPayerUserId(), "PAYMENT_FAILED", "Payment",
					payment.getId(), Map.of("reason", reason == null ? "" : reason));
		});
	}

	@Transactional
	public ReleaseEntity release(UUID escrowId, UserPrincipal actor, EscrowDtos.ReleaseRequest request) {
		accessService.requireAccessible(escrowId, actor);
		accessService.requireRole(escrowId, actor, ParticipantRole.CLIENT);

		EscrowEntity escrow = escrowRepository.findByIdForUpdate(escrowId)
				.orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, "Escrow not found"));
		escrow.assertOneOf(EscrowStatus.AWAITING_RELEASE, EscrowStatus.PARTIALLY_RELEASED, EscrowStatus.DISPUTED);
		escrowService.assertReleaseApprovals(escrowId);

		String idempotencyKey = request.idempotencyKey() != null && !request.idempotencyKey().isBlank()
				? request.idempotencyKey()
				: "release-" + UUID.randomUUID();
		Optional<ReleaseEntity> existing = releaseRepository.findByIdempotencyKey(idempotencyKey);
		if (existing.isPresent()) {
			return existing.get();
		}

		long amount = MoneyUtils.toMinorUnits(request.amount(), escrow.getCurrency());
		EscrowParticipantEntity beneficiary = participantRepository.findById(request.beneficiaryParticipantId())
				.filter(p -> p.getEscrowId().equals(escrowId))
				.orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, "Beneficiary not found"));
		if (beneficiary.getRole() != ParticipantRole.PROVIDER && beneficiary.getRole() != ParticipantRole.BENEFICIARY) {
			throw new DomainException(ErrorCode.PARTICIPANT_INVALID, HttpStatus.BAD_REQUEST,
					"Release target must be a provider or beneficiary");
		}

		List<EscrowParticipantEntity> participants = participantRepository.findByEscrowId(escrowId);
		long entitled = ShareCalculator.entitledAmount(beneficiary, escrow.getAmountTotal(), participants);
		long alreadyReleased = releaseRepository.sumAllocatedAmountForBeneficiary(escrowId, beneficiary.getId());
		long remainingShare = entitled - alreadyReleased;
		if (amount > remainingShare) {
			throw new DomainException(ErrorCode.INSUFFICIENT_FUNDS, HttpStatus.CONFLICT,
					"Release exceeds remaining share for this payee (" + remainingShare + " minor units left)");
		}

		UserEntity payee = userRepository.findById(beneficiary.getUserId())
				.orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, "Payee user not found"));
		if (payee.getStripeConnectAccountId() == null || !payee.isConnectOnboardingComplete()) {
			throw new DomainException(ErrorCode.CONNECT_REQUIRED, HttpStatus.CONFLICT,
					"Beneficiary must complete Stripe Connect onboarding before receiving funds");
		}

		PaymentEntity fundingPayment = paymentRepository
				.findFirstByEscrowIdAndStatusOrderByCreatedAtDesc(escrowId, PaymentStatus.SUCCEEDED)
				.orElseThrow(() -> new DomainException(ErrorCode.PAYMENT_FAILED, HttpStatus.CONFLICT,
						"No successful funding payment found"));
		String sourceChargeId = ensureFundingChargeId(fundingPayment);
		StripeGateway.ChargeSettlement settlement = stripeGateway.retrieveChargeSettlement(sourceChargeId);
		StripeGateway.TransferAmount transferAmount = stripeGateway.resolveTransferAmount(
				amount,
				escrow.getCurrency(),
				settlement
		);
		if (!transferAmount.currency().equalsIgnoreCase(escrow.getCurrency())) {
			log.info(
					"Release FX remap escrowCurrency={} amount={} → transferCurrency={} amount={} charge={}",
					escrow.getCurrency(),
					amount,
					transferAmount.currency(),
					transferAmount.amountMinor(),
					sourceChargeId
			);
		}

		// Reserve funds before calling Stripe so concurrent releases cannot overdraw.
		escrow.applyRelease(amount);
		entityManager.flush();

		ReleaseEntity release = new ReleaseEntity(
				UUID.randomUUID(),
				escrowId,
				actor.getId(),
				beneficiary.getId(),
				amount,
				escrow.getCurrency(),
				idempotencyKey
		);
		releaseRepository.save(release);
		entityManager.flush();

		try {
			String transferId = stripeGateway.createTransfer(
					transferAmount.amountMinor(),
					transferAmount.currency(),
					payee.getStripeConnectAccountId(),
					"escrow_" + escrowId,
					transferAmount.sourceTransaction(),
					release.getId(),
					escrowId,
					"release-" + release.getId()
			);
			release.complete(transferId);
			ledgerEntryRepository.save(new LedgerEntryEntity(
					UUID.randomUUID(),
					escrowId,
					LedgerEntryType.RELEASE,
					-amount,
					escrow.getCurrency(),
					"Release",
					release.getId(),
					"Funds released to participant",
					actor.getId()
			));
			if (escrow.getAmountAvailable() == 0) {
				escrow.transitionTo(EscrowStatus.RELEASED, stateMachine);
			} else if (escrow.getStatus() == EscrowStatus.AWAITING_RELEASE || escrow.getStatus() == EscrowStatus.DISPUTED) {
				escrow.transitionTo(EscrowStatus.PARTIALLY_RELEASED, stateMachine);
			}
			auditService.record(escrowId, actor.getId(), "RELEASE_COMPLETED", "Release", release.getId(),
					Map.of("amount", amount, "transferId", transferId));
			notificationService.notify(beneficiary.getUserId(), escrowId, "RELEASE",
					"Funds released", "Funds were released for escrow \"" + escrow.getTitle() + "\".");
		} catch (RuntimeException ex) {
			escrow.reverseRelease(amount);
			release.fail(ex.getMessage());
			auditService.record(escrowId, actor.getId(), "RELEASE_FAILED", "Release", release.getId(),
					Map.of("error", ex.getMessage() == null ? "" : ex.getMessage()));
			throw ex;
		}
		return release;
	}

	@Transactional
	public RefundEntity refund(UUID escrowId, UserPrincipal actor, EscrowDtos.RefundRequest request) {
		accessService.requireAccessible(escrowId, actor);
		accessService.requireRole(escrowId, actor, ParticipantRole.CLIENT);

		EscrowEntity escrow = escrowRepository.findByIdForUpdate(escrowId)
				.orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, "Escrow not found"));
		escrow.assertOneOf(
				EscrowStatus.FUNDED,
				EscrowStatus.IN_PROGRESS,
				EscrowStatus.AWAITING_RELEASE,
				EscrowStatus.PARTIALLY_RELEASED,
				EscrowStatus.DISPUTED
		);

		String idempotencyKey = request.idempotencyKey() != null && !request.idempotencyKey().isBlank()
				? request.idempotencyKey()
				: "refund-" + UUID.randomUUID();
		Optional<RefundEntity> existing = refundRepository.findByIdempotencyKey(idempotencyKey);
		if (existing.isPresent()) {
			return existing.get();
		}

		long amount = MoneyUtils.toMinorUnits(request.amount(), escrow.getCurrency());
		PaymentEntity fundingPayment = paymentRepository
				.findFirstByEscrowIdAndStatusOrderByCreatedAtDesc(escrowId, PaymentStatus.SUCCEEDED)
				.orElseThrow(() -> new DomainException(ErrorCode.PAYMENT_FAILED, HttpStatus.CONFLICT,
						"No successful funding payment found"));

		escrow.applyRefund(amount);
		entityManager.flush();

		RefundEntity refund = new RefundEntity(
				UUID.randomUUID(),
				escrowId,
				fundingPayment.getId(),
				actor.getId(),
				amount,
				escrow.getCurrency(),
				idempotencyKey
		);
		refundRepository.save(refund);
		entityManager.flush();

		try {
			String stripeRefundId = stripeGateway.createRefund(
					fundingPayment.getStripePaymentIntentId(),
					amount,
					refund.getId(),
					escrowId,
					"refund-" + refund.getId()
			);
			refund.complete(stripeRefundId);
			ledgerEntryRepository.save(new LedgerEntryEntity(
					UUID.randomUUID(),
					escrowId,
					LedgerEntryType.REFUND,
					-amount,
					escrow.getCurrency(),
					"Refund",
					refund.getId(),
					"Funds refunded to payer",
					actor.getId()
			));
			if (escrow.getAmountAvailable() == 0 && escrow.getAmountReleased() == 0) {
				escrow.transitionTo(EscrowStatus.REFUNDED, stateMachine);
			} else if (escrow.getAmountAvailable() == 0) {
				escrow.transitionTo(EscrowStatus.RELEASED, stateMachine);
			}
			auditService.record(escrowId, actor.getId(), "REFUND_COMPLETED", "Refund", refund.getId(),
					Map.of("amount", amount, "stripeRefundId", stripeRefundId));
		} catch (RuntimeException ex) {
			escrow.reverseRefund(amount);
			refund.fail(ex.getMessage());
			auditService.record(escrowId, actor.getId(), "REFUND_FAILED", "Refund", refund.getId(),
					Map.of("error", ex.getMessage() == null ? "" : ex.getMessage()));
			throw ex;
		}
		return refund;
	}

	@Transactional(readOnly = true)
	public Optional<PaymentEntity> findByCheckoutSession(String sessionId) {
		return paymentRepository.findByStripeCheckoutSessionId(sessionId);
	}

	/** Recent releases where the current user is the payee. */
	@Transactional(readOnly = true)
	public List<Map<String, Object>> listMyRecentReleases(UserPrincipal actor) {
		List<EscrowParticipantEntity> mine = participantRepository.findByUserId(actor.getId());
		if (mine.isEmpty()) {
			return List.of();
		}
		List<UUID> participantIds = mine.stream().map(EscrowParticipantEntity::getId).toList();
		Map<UUID, EscrowParticipantEntity> byParticipant = mine.stream()
				.collect(java.util.stream.Collectors.toMap(EscrowParticipantEntity::getId, p -> p));
		List<ReleaseEntity> releases = releaseRepository
				.findTop10ByBeneficiaryParticipantIdInOrderByCreatedAtDesc(participantIds);
		List<Map<String, Object>> rows = new java.util.ArrayList<>();
		for (ReleaseEntity release : releases) {
			EscrowEntity escrow = escrowRepository.findById(release.getEscrowId()).orElse(null);
			EscrowParticipantEntity participant = byParticipant.get(release.getBeneficiaryParticipantId());
			rows.add(Map.of(
					"id", release.getId().toString(),
					"escrowId", release.getEscrowId().toString(),
					"escrowTitle", escrow != null ? escrow.getTitle() : "Escrow",
					"amount", MoneyUtils.toDecimalString(release.getAmount(), release.getCurrency()),
					"currency", release.getCurrency(),
					"status", release.getStatus().name(),
					"role", participant != null ? participant.getRole().name() : "",
					"createdAt", release.getCreatedAt().toString()
			));
		}
		return rows;
	}
}
