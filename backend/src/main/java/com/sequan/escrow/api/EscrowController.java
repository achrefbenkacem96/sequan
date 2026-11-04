package com.sequan.escrow.api;

import com.sequan.escrow.application.EscrowService;
import com.sequan.payment.application.PaymentService;
import com.sequan.payment.domain.RefundEntity;
import com.sequan.payment.domain.ReleaseEntity;
import com.sequan.security.SecurityUtils;
import com.sequan.security.UserPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class EscrowController {

	private final EscrowService escrowService;
	private final PaymentService paymentService;

	public EscrowController(EscrowService escrowService, PaymentService paymentService) {
		this.escrowService = escrowService;
		this.paymentService = paymentService;
	}

	@GetMapping("/dashboard")
	public Map<String, Object> dashboard() {
		return escrowService.dashboardStats(SecurityUtils.requireCurrentUser());
	}

	@GetMapping("/releases/mine")
	public List<Map<String, Object>> myReleases() {
		return paymentService.listMyRecentReleases(SecurityUtils.requireCurrentUser());
	}

	@GetMapping("/escrows")
	public List<EscrowDtos.EscrowResponse> list() {
		return escrowService.list(SecurityUtils.requireCurrentUser());
	}

	@PostMapping("/escrows")
	@ResponseStatus(HttpStatus.CREATED)
	public EscrowDtos.EscrowResponse create(@Valid @RequestBody EscrowDtos.CreateEscrowRequest request) {
		return escrowService.create(SecurityUtils.requireCurrentUser(), request);
	}

	@GetMapping("/escrows/{id}")
	public EscrowDtos.EscrowResponse get(@PathVariable UUID id) {
		return escrowService.get(id, SecurityUtils.requireCurrentUser());
	}

	@PatchMapping("/escrows/{id}")
	public EscrowDtos.EscrowResponse update(@PathVariable UUID id, @Valid @RequestBody EscrowDtos.UpdateEscrowRequest request) {
		return escrowService.update(id, SecurityUtils.requireCurrentUser(), request);
	}

	@PostMapping("/escrows/{id}/publish")
	public EscrowDtos.EscrowResponse publish(@PathVariable UUID id) {
		return escrowService.publish(id, SecurityUtils.requireCurrentUser());
	}

	@PostMapping("/escrows/{id}/cancel")
	public EscrowDtos.EscrowResponse cancel(@PathVariable UUID id) {
		return escrowService.cancel(id, SecurityUtils.requireCurrentUser());
	}

	@PostMapping("/escrows/{id}/participants")
	public EscrowDtos.EscrowResponse addParticipant(
			@PathVariable UUID id,
			@Valid @RequestBody EscrowDtos.AddParticipantRequest request
	) {
		return escrowService.addParticipant(id, SecurityUtils.requireCurrentUser(), request);
	}

	@PostMapping("/escrows/{id}/accept")
	public EscrowDtos.EscrowResponse accept(@PathVariable UUID id) {
		return escrowService.accept(id, SecurityUtils.requireCurrentUser());
	}

	@PostMapping("/escrows/{id}/decline")
	public EscrowDtos.EscrowResponse decline(@PathVariable UUID id) {
		return escrowService.decline(id, SecurityUtils.requireCurrentUser());
	}

	@PostMapping("/escrows/{id}/start")
	public EscrowDtos.EscrowResponse start(@PathVariable UUID id) {
		return escrowService.start(id, SecurityUtils.requireCurrentUser());
	}

	@PostMapping("/escrows/{id}/request-release")
	public EscrowDtos.EscrowResponse requestRelease(@PathVariable UUID id) {
		return escrowService.requestRelease(id, SecurityUtils.requireCurrentUser());
	}

	@PostMapping("/escrows/{id}/approve-release")
	public EscrowDtos.EscrowResponse approveRelease(@PathVariable UUID id) {
		return escrowService.approveRelease(id, SecurityUtils.requireCurrentUser());
	}

	@PostMapping("/escrows/{id}/reject-release")
	public EscrowDtos.EscrowResponse rejectRelease(@PathVariable UUID id) {
		return escrowService.rejectRelease(id, SecurityUtils.requireCurrentUser());
	}

	@PostMapping("/escrows/{id}/conditions/{conditionId}/satisfy")
	public EscrowDtos.EscrowResponse satisfyCondition(@PathVariable UUID id, @PathVariable UUID conditionId) {
		return escrowService.satisfyCondition(id, conditionId, SecurityUtils.requireCurrentUser());
	}

	@PostMapping("/escrows/{id}/fund")
	public EscrowDtos.FundResponse fund(
			@PathVariable UUID id,
			@RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
			@RequestBody(required = false) EscrowDtos.FundRequest request
	) {
		String returnBaseUrl = request == null ? null : request.returnBaseUrl();
		return paymentService.fund(id, SecurityUtils.requireCurrentUser(), idempotencyKey, returnBaseUrl);
	}

	@PostMapping("/escrows/{id}/confirm-funding")
	public EscrowDtos.EscrowResponse confirmFunding(
			@PathVariable UUID id,
			@org.springframework.web.bind.annotation.RequestParam("session_id") String sessionId
	) {
		UserPrincipal actor = SecurityUtils.requireCurrentUser();
		paymentService.confirmFunding(id, actor, sessionId);
		return escrowService.get(id, actor);
	}

	@PostMapping("/escrows/{id}/releases")
	@ResponseStatus(HttpStatus.CREATED)
	public ReleaseResponse release(@PathVariable UUID id, @Valid @RequestBody EscrowDtos.ReleaseRequest request) {
		ReleaseEntity release = paymentService.release(id, SecurityUtils.requireCurrentUser(), request);
		return new ReleaseResponse(release.getId(), release.getStatus().name(), release.getAmount(), release.getCurrency());
	}

	@PostMapping("/escrows/{id}/refunds")
	@ResponseStatus(HttpStatus.CREATED)
	public RefundResponse refund(@PathVariable UUID id, @Valid @RequestBody EscrowDtos.RefundRequest request) {
		RefundEntity refund = paymentService.refund(id, SecurityUtils.requireCurrentUser(), request);
		return new RefundResponse(refund.getId(), refund.getStatus().name(), refund.getAmount(), refund.getCurrency());
	}

	@PostMapping("/escrows/{id}/dispute")
	public EscrowDtos.EscrowResponse dispute(@PathVariable UUID id, @Valid @RequestBody EscrowDtos.DisputeRequest request) {
		return escrowService.openDispute(id, SecurityUtils.requireCurrentUser(), request);
	}

	@PostMapping("/escrows/{id}/dispute/resolve")
	public EscrowDtos.EscrowResponse resolveDispute(
			@PathVariable UUID id,
			@Valid @RequestBody EscrowDtos.ResolveDisputeRequest request
	) {
		UserPrincipal actor = SecurityUtils.requireCurrentUser();
		escrowService.resolveDispute(id, actor, request);
		return escrowService.get(id, actor);
	}

	@GetMapping("/escrows/{id}/events")
	public List<EscrowDtos.TimelineEventResponse> events(@PathVariable UUID id) {
		return escrowService.timeline(id, SecurityUtils.requireCurrentUser());
	}

	public record ReleaseResponse(UUID id, String status, long amount, String currency) {
	}

	public record RefundResponse(UUID id, String status, long amount, String currency) {
	}
}
