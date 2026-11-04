package com.sequan.escrow.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class EscrowDtos {

	private EscrowDtos() {
	}

	public record CreateEscrowRequest(
			@NotBlank @Size(max = 200) String title,
			@Size(max = 5000) String description,
			@NotBlank @Pattern(regexp = "^[A-Za-z]{3}$") String currency,
			@NotBlank String amount,
			Instant fundingDeadline,
			Instant completionDeadline,
			@Valid List<ParticipantRequest> participants,
			@Valid List<ConditionRequest> conditions
	) {
	}

	public record UpdateEscrowRequest(
			@NotBlank @Size(max = 200) String title,
			@Size(max = 5000) String description,
			Instant fundingDeadline,
			Instant completionDeadline
	) {
	}

	public record ParticipantRequest(
			@NotNull UUID userId,
			@NotBlank String role,
			@NotBlank String shareType,
			/** Payees (provider/beneficiary) split the total; approver/arbitrator use 0. */
			@PositiveOrZero long shareValue,
			boolean canApproveRelease
	) {
	}

	public record ConditionRequest(
			@NotBlank @Size(max = 200) String title,
			@Size(max = 2000) String description,
			boolean required
	) {
	}

	public record AddParticipantRequest(
			@NotNull UUID userId,
			@NotBlank String role,
			@NotBlank String shareType,
			@PositiveOrZero long shareValue,
			boolean canApproveRelease
	) {
	}

	public record DisputeRequest(@NotBlank @Size(max = 2000) String reason) {
	}

	public record ResolveDisputeRequest(
			@NotBlank String resolution,
			@Size(max = 2000) String notes
	) {
	}

	public record FundRequest(
			String returnBaseUrl
	) {
	}

	public record FundResponse(
			UUID paymentId,
			String checkoutSessionId,
			String clientSecret,
			String publishableKey,
			String checkoutUrl
	) {
	}

	public record ReleaseRequest(
			@NotNull UUID beneficiaryParticipantId,
			@NotBlank String amount,
			String idempotencyKey
	) {
	}

	public record RefundRequest(
			@NotBlank String amount,
			String idempotencyKey
	) {
	}

	public record EscrowResponse(
			UUID id,
			String title,
			String description,
			String currency,
			String amountTotal,
			String amountFunded,
			String amountReleased,
			String amountRefunded,
			String amountAvailable,
			String status,
			Instant fundingDeadline,
			Instant completionDeadline,
			UUID createdBy,
			List<ParticipantResponse> participants,
			List<ConditionResponse> conditions,
			List<String> availableActions,
			Instant createdAt,
			Instant updatedAt
	) {
	}

	public record ParticipantResponse(
			UUID id,
			UUID userId,
			String email,
			String fullName,
			String role,
			String shareType,
			long shareValue,
			/** Decimal major units this payee is entitled to (null for non-payees). */
			String entitledAmount,
			/** Decimal major units still releasable to this payee (null for non-payees). */
			String remainingShare,
			String participationStatus,
			String approvalStatus,
			boolean canApproveRelease
	) {
	}

	public record ConditionResponse(
			UUID id,
			String title,
			String description,
			int sortOrder,
			String status,
			boolean required
	) {
	}

	public record TimelineEventResponse(
			UUID id,
			String action,
			UUID actorUserId,
			String entityType,
			UUID entityId,
			Map<String, Object> metadata,
			Instant createdAt
	) {
	}
}
