package com.sequan.payment.domain;

public enum PaymentStatus {
	PENDING,
	REQUIRES_ACTION,
	SUCCEEDED,
	FAILED,
	CANCELLED
}
