package com.sequan.payment.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RefundRepository extends JpaRepository<RefundEntity, UUID> {
	List<RefundEntity> findByEscrowIdOrderByCreatedAtDesc(UUID escrowId);

	Optional<RefundEntity> findByIdempotencyKey(String idempotencyKey);

	Optional<RefundEntity> findByStripeRefundId(String stripeRefundId);
}
