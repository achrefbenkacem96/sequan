package com.sequan.escrow.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EscrowRepository extends JpaRepository<EscrowEntity, UUID> {

	@Query("""
			select distinct e from EscrowEntity e
			where e.createdBy = :userId
			   or (
					e.status <> com.sequan.escrow.domain.EscrowStatus.DRAFT
					and e.id in (
						select p.escrowId from EscrowParticipantEntity p where p.userId = :userId
					)
			   )
			order by e.updatedAt desc
			""")
	List<EscrowEntity> findAccessibleByUser(@Param("userId") UUID userId);

	@Query("""
			select e from EscrowEntity e
			where e.status = com.sequan.escrow.domain.EscrowStatus.PENDING_FUNDING
			  and e.fundingDeadline is not null
			  and e.fundingDeadline < :now
			""")
	List<EscrowEntity> findExpiredPendingFunding(@Param("now") Instant now);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select e from EscrowEntity e where e.id = :id")
	Optional<EscrowEntity> findByIdForUpdate(@Param("id") UUID id);
}
