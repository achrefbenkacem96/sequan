package com.sequan.audit;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class AuditService {

	private final AuditLogRepository auditLogRepository;

	public AuditService(AuditLogRepository auditLogRepository) {
		this.auditLogRepository = auditLogRepository;
	}

	@Transactional
	public void record(UUID escrowId, UUID actorUserId, String action, String entityType, UUID entityId, Map<String, Object> metadata) {
		auditLogRepository.save(new AuditLogEntity(
				UUID.randomUUID(),
				escrowId,
				actorUserId,
				action,
				entityType,
				entityId,
				metadata
		));
	}

	@Transactional(readOnly = true)
	public List<AuditLogEntity> timeline(UUID escrowId) {
		return auditLogRepository.findByEscrowIdOrderByCreatedAtAsc(escrowId);
	}
}
