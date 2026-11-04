package com.sequan.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "audit_logs")
public class AuditLogEntity {

	@Id
	private UUID id;

	@Column(name = "escrow_id")
	private UUID escrowId;

	@Column(name = "actor_user_id")
	private UUID actorUserId;

	@Column(nullable = false, length = 80)
	private String action;

	@Column(name = "entity_type", length = 64)
	private String entityType;

	@Column(name = "entity_id")
	private UUID entityId;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(columnDefinition = "jsonb")
	private Map<String, Object> metadata;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	protected AuditLogEntity() {
	}

	public AuditLogEntity(
			UUID id,
			UUID escrowId,
			UUID actorUserId,
			String action,
			String entityType,
			UUID entityId,
			Map<String, Object> metadata
	) {
		this.id = id;
		this.escrowId = escrowId;
		this.actorUserId = actorUserId;
		this.action = action;
		this.entityType = entityType;
		this.entityId = entityId;
		this.metadata = metadata;
		this.createdAt = Instant.now();
	}

	public UUID getId() {
		return id;
	}

	public UUID getEscrowId() {
		return escrowId;
	}

	public UUID getActorUserId() {
		return actorUserId;
	}

	public String getAction() {
		return action;
	}

	public String getEntityType() {
		return entityType;
	}

	public UUID getEntityId() {
		return entityId;
	}

	public Map<String, Object> getMetadata() {
		return metadata;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
