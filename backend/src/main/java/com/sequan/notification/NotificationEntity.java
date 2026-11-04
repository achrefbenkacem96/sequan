package com.sequan.notification;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "notifications")
public class NotificationEntity {

	@Id
	private UUID id;

	@Column(name = "user_id", nullable = false)
	private UUID userId;

	@Column(name = "escrow_id")
	private UUID escrowId;

	@Column(nullable = false, length = 64)
	private String type;

	@Column(nullable = false, length = 200)
	private String title;

	@Column(nullable = false)
	private String message;

	@Column(name = "read_at")
	private Instant readAt;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	protected NotificationEntity() {
	}

	public NotificationEntity(UUID id, UUID userId, UUID escrowId, String type, String title, String message) {
		this.id = id;
		this.userId = userId;
		this.escrowId = escrowId;
		this.type = type;
		this.title = title;
		this.message = message;
		this.createdAt = Instant.now();
	}

	public UUID getId() {
		return id;
	}

	public UUID getUserId() {
		return userId;
	}

	public UUID getEscrowId() {
		return escrowId;
	}

	public String getType() {
		return type;
	}

	public String getTitle() {
		return title;
	}

	public String getMessage() {
		return message;
	}

	public Instant getReadAt() {
		return readAt;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public void markRead() {
		this.readAt = Instant.now();
	}
}
