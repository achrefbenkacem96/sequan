package com.sequan.payment.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReleaseRepository extends JpaRepository<ReleaseEntity, UUID> {
	List<ReleaseEntity> findByEscrowIdOrderByCreatedAtDesc(UUID escrowId);

	List<ReleaseEntity> findTop10ByBeneficiaryParticipantIdInOrderByCreatedAtDesc(
			java.util.Collection<UUID> beneficiaryParticipantIds
	);

	Optional<ReleaseEntity> findByIdempotencyKey(String idempotencyKey);

	@Query("""
			select coalesce(sum(r.amount), 0) from ReleaseEntity r
			where r.escrowId = :escrowId
			  and r.beneficiaryParticipantId = :beneficiaryParticipantId
			  and r.status <> com.sequan.payment.domain.ReleaseStatus.FAILED
			""")
	long sumAllocatedAmountForBeneficiary(
			@Param("escrowId") UUID escrowId,
			@Param("beneficiaryParticipantId") UUID beneficiaryParticipantId
	);

	/** @deprecated use {@link #sumAllocatedAmountForBeneficiary} */
	default long sumCompletedAmountForBeneficiary(UUID escrowId, UUID beneficiaryParticipantId) {
		return sumAllocatedAmountForBeneficiary(escrowId, beneficiaryParticipantId);
	}
}
