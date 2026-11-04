package com.sequan.notification;

import com.sequan.security.SecurityUtils;
import com.sequan.security.UserPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

	private final NotificationService notificationService;

	public NotificationController(NotificationService notificationService) {
		this.notificationService = notificationService;
	}

	@GetMapping
	public List<NotificationResponse> list() {
		UserPrincipal user = SecurityUtils.requireCurrentUser();
		return notificationService.listForUser(user.getId()).stream()
				.map(n -> new NotificationResponse(
						n.getId(),
						n.getEscrowId(),
						n.getType(),
						n.getTitle(),
						n.getMessage(),
						n.getReadAt(),
						n.getCreatedAt()
				))
				.toList();
	}

	@PostMapping("/{id}/read")
	public void markRead(@PathVariable UUID id) {
		UserPrincipal user = SecurityUtils.requireCurrentUser();
		notificationService.markRead(user.getId(), id);
	}

	public record NotificationResponse(
			UUID id,
			UUID escrowId,
			String type,
			String title,
			String message,
			Instant readAt,
			Instant createdAt
	) {
	}
}
