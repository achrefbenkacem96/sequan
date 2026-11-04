package com.sequan.payment.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payments")
public class PaymentEntity {

	@Id
	private UUID id;

	@Column(name = "escrow_id", nullable = false)
	private UUID escrowId;

	@Column(name = "payer_user_id", nullable = false)
	private UUID payerUserId;

	@Column(nullable = false)
	private long amount;

	@Column(nullable = false, length = 3)
	private String currency;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 32)
	private PaymentStatus status;

	@Column(name = "stripe_checkout_session_id", unique = true)
	private String stripeCheckoutSessionId;

	@Column(name = "stripe_payment_intent_id")
	private String stripePaymentIntentId;

	@Column(name = "stripe_charge_id")
	private String stripeChargeId;

	@Column(name = "idempotency_key", unique = true, length = 128)
	private String idempotencyKey;

	@Column(name = "failure_reason")
	private String failureReason;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected PaymentEntity() {
	}

	public PaymentEntity(UUID id, UUID escrowId, UUID payerUserId, long amount, String currency, String idempotencyKey) {
		Instant now = Instant.now();
		this.id = id;
		this.escrowId = escrowId;
		this.payerUserId = payerUserId;
		this.amount = amount;
		this.currency = currency;
		this.status = PaymentStatus.PENDING;
		this.idempotencyKey = idempotencyKey;
		this.createdAt = now;
		this.updatedAt = now;
	}

	public void attachCheckoutSession(String sessionId) {
		this.stripeCheckoutSessionId = sessionId;
		this.status = PaymentStatus.REQUIRES_ACTION;
		touch();
	}

	public void markSucceeded(String paymentIntentId, String chargeId) {
		this.stripePaymentIntentId = paymentIntentId;
		if (chargeId != null && !chargeId.isBlank()) {
			this.stripeChargeId = chargeId;
		}
		this.status = PaymentStatus.SUCCEEDED;
		touch();
	}

	/**
	 * Persists a charge id discovered after the payment already succeeded
	 * (e.g. from PaymentIntent.latest_charge backfill).
	 */
	public void attachChargeId(String chargeId) {
		if (chargeId == null || chargeId.isBlank()) {
			return;
		}
		if (chargeId.equals(this.stripeChargeId)) {
			return;
		}
		this.stripeChargeId = chargeId;
		touch();
	}

	public void markFailed(String reason) {
		this.failureReason = reason;
		this.status = PaymentStatus.FAILED;
		touch();
	}

	private void touch() {
		this.updatedAt = Instant.now();
	}

	public UUID getId() {
		return id;
	}

	public UUID getEscrowId() {
		return escrowId;
	}

	public UUID getPayerUserId() {
		return payerUserId;
	}

	public long getAmount() {
		return amount;
	}

	public String getCurrency() {
		return currency;
	}

	public PaymentStatus getStatus() {
		return status;
	}

	public String getStripeCheckoutSessionId() {
		return stripeCheckoutSessionId;
	}

	public String getStripePaymentIntentId() {
		return stripePaymentIntentId;
	}

	public String getStripeChargeId() {
		return stripeChargeId;
	}

	public String getIdempotencyKey() {
		return idempotencyKey;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}
}
