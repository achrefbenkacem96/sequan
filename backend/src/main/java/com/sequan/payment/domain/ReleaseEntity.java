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
@Table(name = "releases")
public class ReleaseEntity {

	@Id
	private UUID id;

	@Column(name = "escrow_id", nullable = false)
	private UUID escrowId;

	@Column(name = "requested_by", nullable = false)
	private UUID requestedBy;

	@Column(name = "beneficiary_participant_id", nullable = false)
	private UUID beneficiaryParticipantId;

	@Column(nullable = false)
	private long amount;

	@Column(nullable = false, length = 3)
	private String currency;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 32)
	private ReleaseStatus status;

	@Column(name = "stripe_transfer_id", unique = true)
	private String stripeTransferId;

	@Column(name = "idempotency_key", unique = true, length = 128)
	private String idempotencyKey;

	@Column(name = "failure_reason")
	private String failureReason;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected ReleaseEntity() {
	}

	public ReleaseEntity(
			UUID id,
			UUID escrowId,
			UUID requestedBy,
			UUID beneficiaryParticipantId,
			long amount,
			String currency,
			String idempotencyKey
	) {
		Instant now = Instant.now();
		this.id = id;
		this.escrowId = escrowId;
		this.requestedBy = requestedBy;
		this.beneficiaryParticipantId = beneficiaryParticipantId;
		this.amount = amount;
		this.currency = currency;
		this.status = ReleaseStatus.PENDING;
		this.idempotencyKey = idempotencyKey;
		this.createdAt = now;
		this.updatedAt = now;
	}

	public void complete(String transferId) {
		this.stripeTransferId = transferId;
		this.status = ReleaseStatus.COMPLETED;
		this.updatedAt = Instant.now();
	}

	public void fail(String reason) {
		this.failureReason = reason;
		this.status = ReleaseStatus.FAILED;
		this.updatedAt = Instant.now();
	}

	public UUID getId() {
		return id;
	}

	public UUID getEscrowId() {
		return escrowId;
	}

	public UUID getRequestedBy() {
		return requestedBy;
	}

	public UUID getBeneficiaryParticipantId() {
		return beneficiaryParticipantId;
	}

	public long getAmount() {
		return amount;
	}

	public String getCurrency() {
		return currency;
	}

	public ReleaseStatus getStatus() {
		return status;
	}

	public String getStripeTransferId() {
		return stripeTransferId;
	}

	public String getIdempotencyKey() {
		return idempotencyKey;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
