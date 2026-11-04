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
@Table(name = "disputes")
public class DisputeEntity {

	@Id
	private UUID id;

	@Column(name = "escrow_id", nullable = false)
	private UUID escrowId;

	@Column(name = "opened_by", nullable = false)
	private UUID openedBy;

	@Column(nullable = false)
	private String reason;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 32)
	private DisputeStatus status;

	@Column(name = "resolution_notes")
	private String resolutionNotes;

	@Column(name = "resolved_by")
	private UUID resolvedBy;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected DisputeEntity() {
	}

	public DisputeEntity(UUID id, UUID escrowId, UUID openedBy, String reason) {
		Instant now = Instant.now();
		this.id = id;
		this.escrowId = escrowId;
		this.openedBy = openedBy;
		this.reason = reason;
		this.status = DisputeStatus.OPEN;
		this.createdAt = now;
		this.updatedAt = now;
	}

	public void resolve(DisputeStatus status, UUID resolvedBy, String notes) {
		this.status = status;
		this.resolvedBy = resolvedBy;
		this.resolutionNotes = notes;
		this.updatedAt = Instant.now();
	}

	public UUID getId() {
		return id;
	}

	public UUID getEscrowId() {
		return escrowId;
	}

	public UUID getOpenedBy() {
		return openedBy;
	}

	public String getReason() {
		return reason;
	}

	public DisputeStatus getStatus() {
		return status;
	}

	public String getResolutionNotes() {
		return resolutionNotes;
	}

	public UUID getResolvedBy() {
		return resolvedBy;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
