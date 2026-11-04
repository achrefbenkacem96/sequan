package com.sequan.webhook;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sequan.common.error.DomainException;
import com.sequan.config.SequanProperties;
import com.sequan.payment.application.PaymentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class StripeWebhookServiceTest {

	@Mock
	private WebhookEventRepository webhookEventRepository;
	@Mock
	private PaymentService paymentService;

	private StripeWebhookService service;

	@BeforeEach
	void setUp() {
		SequanProperties properties = new SequanProperties(
				new SequanProperties.Jwt("test-secret-key-that-is-long-enough-for-hs256", 900000, 604800000),
				new SequanProperties.Cors("http://localhost:4200"),
				new SequanProperties.App("http://localhost:4200", "http://localhost:8080"),
				new SequanProperties.Stripe("sk_test", "pk_test", "whsec_test")
		);
		service = new StripeWebhookService(properties, webhookEventRepository, paymentService, new ObjectMapper());
	}

	@Test
	void rejectsInvalidSignature() {
		assertThrows(DomainException.class, () -> service.handle("{}", "invalid"));
		verify(paymentService, never()).markPaymentSucceeded(anyString(), anyString(), anyString());
	}

	@Test
	void rejectsMissingSignatureMaterial() {
		assertThrows(DomainException.class, () -> service.handle("{\"id\":\"evt_1\"}", "t=1,v1=abc"));
		verify(webhookEventRepository, never()).save(org.mockito.ArgumentMatchers.any());
	}
}
