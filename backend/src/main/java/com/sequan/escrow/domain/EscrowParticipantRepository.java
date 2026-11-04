package com.sequan.escrow.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EscrowParticipantRepository extends JpaRepository<EscrowParticipantEntity, UUID> {
	List<EscrowParticipantEntity> findByEscrowId(UUID escrowId);

	Optional<EscrowParticipantEntity> findByEscrowIdAndUserId(UUID escrowId, UUID userId);

	List<EscrowParticipantEntity> findByUserId(UUID userId);

	boolean existsByEscrowIdAndUserId(UUID escrowId, UUID userId);
}
