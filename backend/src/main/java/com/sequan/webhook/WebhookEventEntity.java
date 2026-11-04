package com.sequan.webhook;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "webhook_events")
public class WebhookEventEntity {

	@Id
	private UUID id;

	@Column(name = "stripe_event_id", nullable = false, unique = true)
	private String stripeEventId;

	@Column(name = "event_type", nullable = false, length = 120)
	private String eventType;

	@Column(name = "payload_hash", nullable = false, length = 128)
	private String payloadHash;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(nullable = false, columnDefinition = "jsonb")
	private Map<String, Object> payload;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 32)
	private WebhookEventStatus status;

	@Column(nullable = false)
	private int attempts;

	@Column(name = "last_error")
	private String lastError;

	@Column(name = "processed_at")
	private Instant processedAt;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected WebhookEventEntity() {
	}

	public WebhookEventEntity(UUID id, String stripeEventId, String eventType, String payloadHash, Map<String, Object> payload) {
		Instant now = Instant.now();
		this.id = id;
		this.stripeEventId = stripeEventId;
		this.eventType = eventType;
		this.payloadHash = payloadHash;
		this.payload = payload;
		this.status = WebhookEventStatus.RECEIVED;
		this.attempts = 0;
		this.createdAt = now;
		this.updatedAt = now;
	}

	public void markProcessed() {
		this.status = WebhookEventStatus.PROCESSED;
		this.processedAt = Instant.now();
		this.updatedAt = Instant.now();
		this.attempts += 1;
	}

	public void markFailed(String error) {
		this.status = WebhookEventStatus.FAILED;
		this.lastError = error;
		this.updatedAt = Instant.now();
		this.attempts += 1;
	}

	public UUID getId() {
		return id;
	}

	public String getStripeEventId() {
		return stripeEventId;
	}

	public String getEventType() {
		return eventType;
	}

	public WebhookEventStatus getStatus() {
		return status;
	}

	public Map<String, Object> getPayload() {
		return payload;
	}
}
