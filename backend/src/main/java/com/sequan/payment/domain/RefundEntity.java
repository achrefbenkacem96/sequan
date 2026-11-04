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
@Table(name = "refunds")
public class RefundEntity {

	@Id
	private UUID id;

	@Column(name = "escrow_id", nullable = false)
	private UUID escrowId;

	@Column(name = "payment_id", nullable = false)
	private UUID paymentId;

	@Column(name = "requested_by", nullable = false)
	private UUID requestedBy;

	@Column(nullable = false)
	private long amount;

	@Column(nullable = false, length = 3)
	private String currency;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 32)
	private RefundStatus status;

	@Column(name = "stripe_refund_id", unique = true)
	private String stripeRefundId;

	@Column(name = "idempotency_key", unique = true, length = 128)
	private String idempotencyKey;

	@Column(name = "failure_reason")
	private String failureReason;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected RefundEntity() {
	}

	public RefundEntity(
			UUID id,
			UUID escrowId,
			UUID paymentId,
			UUID requestedBy,
			long amount,
			String currency,
			String idempotencyKey
	) {
		Instant now = Instant.now();
		this.id = id;
		this.escrowId = escrowId;
		this.paymentId = paymentId;
		this.requestedBy = requestedBy;
		this.amount = amount;
		this.currency = currency;
		this.status = RefundStatus.PENDING;
		this.idempotencyKey = idempotencyKey;
		this.createdAt = now;
		this.updatedAt = now;
	}

	public void complete(String stripeRefundId) {
		this.stripeRefundId = stripeRefundId;
		this.status = RefundStatus.COMPLETED;
		this.updatedAt = Instant.now();
	}

	public void fail(String reason) {
		this.failureReason = reason;
		this.status = RefundStatus.FAILED;
		this.updatedAt = Instant.now();
	}

	public UUID getId() {
		return id;
	}

	public UUID getEscrowId() {
		return escrowId;
	}

	public UUID getPaymentId() {
		return paymentId;
	}

	public UUID getRequestedBy() {
		return requestedBy;
	}

	public long getAmount() {
		return amount;
	}

	public String getCurrency() {
		return currency;
	}

	public RefundStatus getStatus() {
		return status;
	}

	public String getStripeRefundId() {
		return stripeRefundId;
	}

	public String getIdempotencyKey() {
		return idempotencyKey;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
