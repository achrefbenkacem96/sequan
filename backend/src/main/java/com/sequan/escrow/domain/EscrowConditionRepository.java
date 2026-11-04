package com.sequan.escrow.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EscrowConditionRepository extends JpaRepository<EscrowConditionEntity, UUID> {
	List<EscrowConditionEntity> findByEscrowIdOrderBySortOrderAsc(UUID escrowId);

	Optional<EscrowConditionEntity> findByIdAndEscrowId(UUID id, UUID escrowId);
}
