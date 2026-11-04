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
@Table(name = "escrow_participants")
public class EscrowParticipantEntity {

	@Id
	private UUID id;

	@Column(name = "escrow_id", nullable = false)
	private UUID escrowId;

	@Column(name = "user_id", nullable = false)
	private UUID userId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 32)
	private ParticipantRole role;

	@Enumerated(EnumType.STRING)
	@Column(name = "share_type", nullable = false, length = 16)
	private ShareType shareType;

	@Column(name = "share_value", nullable = false)
	private long shareValue;

	@Enumerated(EnumType.STRING)
	@Column(name = "participation_status", nullable = false, length = 32)
	private ParticipationStatus participationStatus;

	@Enumerated(EnumType.STRING)
	@Column(name = "approval_status", nullable = false, length = 32)
	private ApprovalStatus approvalStatus;

	@Column(name = "can_approve_release", nullable = false)
	private boolean canApproveRelease;

	@Column(name = "invited_email", length = 320)
	private String invitedEmail;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected EscrowParticipantEntity() {
	}

	public EscrowParticipantEntity(
			UUID id,
			UUID escrowId,
			UUID userId,
			ParticipantRole role,
			ShareType shareType,
			long shareValue,
			ParticipationStatus participationStatus,
			boolean canApproveRelease,
			String invitedEmail
	) {
		Instant now = Instant.now();
		this.id = id;
		this.escrowId = escrowId;
		this.userId = userId;
		this.role = role;
		this.shareType = shareType;
		this.shareValue = shareValue;
		this.participationStatus = participationStatus;
		this.canApproveRelease = canApproveRelease;
		this.approvalStatus = canApproveRelease ? ApprovalStatus.PENDING : ApprovalStatus.NOT_REQUIRED;
		this.invitedEmail = invitedEmail;
		this.createdAt = now;
		this.updatedAt = now;
	}

	public void accept() {
		this.participationStatus = ParticipationStatus.ACCEPTED;
		touch();
	}

	public void decline() {
		this.participationStatus = ParticipationStatus.DECLINED;
		touch();
	}

	public void approveRelease() {
		this.approvalStatus = ApprovalStatus.APPROVED;
		touch();
	}

	public void rejectRelease() {
		this.approvalStatus = ApprovalStatus.REJECTED;
		touch();
	}

	public void resetApproval() {
		if (canApproveRelease) {
			this.approvalStatus = ApprovalStatus.PENDING;
			touch();
		}
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

	public UUID getUserId() {
		return userId;
	}

	public ParticipantRole getRole() {
		return role;
	}

	public ShareType getShareType() {
		return shareType;
	}

	public long getShareValue() {
		return shareValue;
	}

	public ParticipationStatus getParticipationStatus() {
		return participationStatus;
	}

	public ApprovalStatus getApprovalStatus() {
		return approvalStatus;
	}

	public boolean isCanApproveRelease() {
		return canApproveRelease;
	}

	public String getInvitedEmail() {
		return invitedEmail;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}
}
