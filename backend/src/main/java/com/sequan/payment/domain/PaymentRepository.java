package com.sequan.payment.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<PaymentEntity, UUID> {
	List<PaymentEntity> findByEscrowIdOrderByCreatedAtDesc(UUID escrowId);

	Optional<PaymentEntity> findByStripeCheckoutSessionId(String sessionId);

	Optional<PaymentEntity> findByStripePaymentIntentId(String paymentIntentId);

	Optional<PaymentEntity> findByIdempotencyKey(String idempotencyKey);

	Optional<PaymentEntity> findFirstByEscrowIdAndStatusOrderByCreatedAtDesc(UUID escrowId, PaymentStatus status);
}
