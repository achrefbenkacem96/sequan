package com.sequan.escrow.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "escrow_conditions")
public class EscrowConditionEntity {

	@Id
	private UUID id;

	@Column(name = "escrow_id", nullable = false)
	private UUID escrowId;

	@Column(nullable = false, length = 200)
	private String title;

	@Column
	private String description;

	@Column(name = "sort_order", nullable = false)
	private int sortOrder;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 32)
	private ConditionStatus status;

	@Column(nullable = false)
	private boolean required;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected EscrowConditionEntity() {
	}

	public EscrowConditionEntity(
			UUID id,
			UUID escrowId,
			String title,
			String description,
			int sortOrder,
			boolean required
	) {
		Instant now = Instant.now();
		this.id = id;
		this.escrowId = escrowId;
		this.title = title;
		this.description = description;
		this.sortOrder = sortOrder;
		this.required = required;
		this.status = ConditionStatus.PENDING;
		this.createdAt = now;
		this.updatedAt = now;
	}

	public void satisfy() {
		this.status = ConditionStatus.SATISFIED;
		this.updatedAt = Instant.now();
	}

	public void waive() {
		this.status = ConditionStatus.WAIVED;
		this.updatedAt = Instant.now();
	}

	public void reject() {
		this.status = ConditionStatus.REJECTED;
		this.updatedAt = Instant.now();
	}

	public UUID getId() {
		return id;
	}

	public UUID getEscrowId() {
		return escrowId;
	}

	public String getTitle() {
		return title;
	}

	public String getDescription() {
		return description;
	}

	public int getSortOrder() {
		return sortOrder;
	}

	public ConditionStatus getStatus() {
		return status;
	}

	public boolean isRequired() {
		return required;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}
}
