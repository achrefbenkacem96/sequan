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
@Table(name = "ledger_entries")
public class LedgerEntryEntity {

	@Id
	private UUID id;

	@Column(name = "escrow_id", nullable = false)
	private UUID escrowId;

	@Enumerated(EnumType.STRING)
	@Column(name = "entry_type", nullable = false, length = 32)
	private LedgerEntryType entryType;

	@Column(nullable = false)
	private long amount;

	@Column(nullable = false, length = 3)
	private String currency;

	@Column(name = "reference_type", length = 32)
	private String referenceType;

	@Column(name = "reference_id")
	private UUID referenceId;

	@Column(length = 500)
	private String description;

	@Column(name = "created_by")
	private UUID createdBy;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	protected LedgerEntryEntity() {
	}

	public LedgerEntryEntity(
			UUID id,
			UUID escrowId,
			LedgerEntryType entryType,
			long amount,
			String currency,
			String referenceType,
			UUID referenceId,
			String description,
			UUID createdBy
	) {
		this.id = id;
		this.escrowId = escrowId;
		this.entryType = entryType;
		this.amount = amount;
		this.currency = currency;
		this.referenceType = referenceType;
		this.referenceId = referenceId;
		this.description = description;
		this.createdBy = createdBy;
		this.createdAt = Instant.now();
	}

	public UUID getId() {
		return id;
	}

	public UUID getEscrowId() {
		return escrowId;
	}

	public LedgerEntryType getEntryType() {
		return entryType;
	}

	public long getAmount() {
		return amount;
	}

	public String getCurrency() {
		return currency;
	}

	public String getReferenceType() {
		return referenceType;
	}

	public UUID getReferenceId() {
		return referenceId;
	}

	public String getDescription() {
		return description;
	}

	public UUID getCreatedBy() {
		return createdBy;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
