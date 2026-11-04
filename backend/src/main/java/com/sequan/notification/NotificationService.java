package com.sequan.notification;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class NotificationService {

	private final NotificationRepository notificationRepository;

	public NotificationService(NotificationRepository notificationRepository) {
		this.notificationRepository = notificationRepository;
	}

	@Transactional
	public void notify(UUID userId, UUID escrowId, String type, String title, String message) {
		notificationRepository.save(new NotificationEntity(
				UUID.randomUUID(),
				userId,
				escrowId,
				type,
				title,
				message
		));
	}

	@Transactional(readOnly = true)
	public List<NotificationEntity> listForUser(UUID userId) {
		return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId);
	}

	@Transactional
	public void markRead(UUID userId, UUID notificationId) {
		notificationRepository.findById(notificationId)
				.filter(n -> n.getUserId().equals(userId))
				.ifPresent(NotificationEntity::markRead);
	}
}
