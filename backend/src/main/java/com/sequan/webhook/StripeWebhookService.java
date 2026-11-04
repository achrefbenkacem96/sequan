package com.sequan.webhook;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sequan.common.error.DomainException;
import com.sequan.common.error.ErrorCode;
import com.sequan.config.SequanProperties;
import com.sequan.payment.application.PaymentService;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.model.EventDataObjectDeserializer;
import com.stripe.model.PaymentIntent;
import com.stripe.model.StripeObject;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class StripeWebhookService {

	private static final Logger log = LoggerFactory.getLogger(StripeWebhookService.class);

	private final SequanProperties properties;
	private final WebhookEventRepository webhookEventRepository;
	private final PaymentService paymentService;
	private final ObjectMapper objectMapper;

	public StripeWebhookService(
			SequanProperties properties,
			WebhookEventRepository webhookEventRepository,
			PaymentService paymentService,
			ObjectMapper objectMapper
	) {
		this.properties = properties;
		this.webhookEventRepository = webhookEventRepository;
		this.paymentService = paymentService;
		this.objectMapper = objectMapper;
	}

	@Transactional
	public void handle(String payload, String signatureHeader) {
		Event event = constructEvent(payload, signatureHeader);

		Optional<WebhookEventEntity> existing = webhookEventRepository.findByStripeEventId(event.getId());
		if (existing.isPresent()) {
			WebhookEventEntity webhookEvent = existing.get();
			if (webhookEvent.getStatus() == WebhookEventStatus.PROCESSED) {
				log.info("Duplicate Stripe event ignored: {}", event.getId());
				return;
			}
			log.info("Retrying previously failed Stripe event: {}", event.getId());
			processAndMark(webhookEvent, event);
			return;
		}

		Map<String, Object> payloadMap = parsePayload(payload);
		WebhookEventEntity webhookEvent;
		try {
			webhookEvent = webhookEventRepository.save(new WebhookEventEntity(
					UUID.randomUUID(),
					event.getId(),
					event.getType(),
					hash(payload),
					payloadMap
			));
		} catch (DataIntegrityViolationException ex) {
			log.info("Concurrent duplicate Stripe event ignored: {}", event.getId());
			return;
		}

		processAndMark(webhookEvent, event);
	}

	private void processAndMark(WebhookEventEntity webhookEvent, Event event) {
		try {
			process(event);
			webhookEvent.markProcessed();
		} catch (RuntimeException ex) {
			log.error("Failed processing Stripe event {}", event.getId(), ex);
			webhookEvent.markFailed(ex.getMessage());
			throw ex;
		}
	}

	private void process(Event event) {
		switch (event.getType()) {
			case "checkout.session.completed" -> handleCheckoutCompleted(event);
			case "payment_intent.succeeded" -> handlePaymentIntentSucceeded(event);
			case "payment_intent.payment_failed" -> handlePaymentIntentFailed(event);
			default -> log.info("Unhandled Stripe event type: {}", event.getType());
		}
	}

	private void handleCheckoutCompleted(Event event) {
		Session session = deserialize(event, Session.class);
		if (session == null) {
			return;
		}
		// Charge id is resolved inside PaymentService from the PaymentIntent when omitted.
		paymentService.markPaymentSucceeded(session.getId(), session.getPaymentIntent(), null);
	}

	private void handlePaymentIntentSucceeded(Event event) {
		PaymentIntent intent = deserialize(event, PaymentIntent.class);
		if (intent == null) {
			return;
		}
		try {
			paymentService.markPaymentSucceeded(null, intent.getId(), intent.getLatestCharge());
		} catch (DomainException ex) {
			if (ex.getCode() != ErrorCode.NOT_FOUND) {
				throw ex;
			}
			log.info("payment_intent.succeeded without local payment record yet: {}", intent.getId());
		}
	}

	private void handlePaymentIntentFailed(Event event) {
		PaymentIntent intent = deserialize(event, PaymentIntent.class);
		if (intent == null) {
			return;
		}
		String reason = intent.getLastPaymentError() != null
				? intent.getLastPaymentError().getMessage()
				: "payment_failed";
		paymentService.markPaymentFailed(intent.getId(), reason);
	}

	private Event constructEvent(String payload, String signatureHeader) {
		try {
			return Webhook.constructEvent(payload, signatureHeader, properties.stripe().webhookSecret());
		} catch (SignatureVerificationException e) {
			throw new DomainException(ErrorCode.WEBHOOK_INVALID, HttpStatus.BAD_REQUEST, "Invalid Stripe signature");
		}
	}

	@SuppressWarnings("unchecked")
	private <T extends StripeObject> T deserialize(Event event, Class<T> type) {
		EventDataObjectDeserializer deserializer = event.getDataObjectDeserializer();
		StripeObject object = deserializer.getObject().orElse(null);
		if (object == null) {
			log.warn("Unable to deserialize Stripe object for event {}", event.getId());
			return null;
		}
		if (!type.isInstance(object)) {
			log.warn("Unexpected Stripe object type {} for event {}", object.getClass().getSimpleName(), event.getId());
			return null;
		}
		return (T) object;
	}

	private Map<String, Object> parsePayload(String payload) {
		try {
			return objectMapper.readValue(payload, new TypeReference<>() {
			});
		} catch (Exception e) {
			return Map.of("raw", true);
		}
	}

	private String hash(String payload) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			return HexFormat.of().formatHex(digest.digest(payload.getBytes(StandardCharsets.UTF_8)));
		} catch (Exception e) {
			return "hash_error";
		}
	}
}
