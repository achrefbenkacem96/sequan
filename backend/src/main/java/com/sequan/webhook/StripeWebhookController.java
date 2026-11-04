package com.sequan.webhook;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/webhooks")
public class StripeWebhookController {

	private final StripeWebhookService stripeWebhookService;

	public StripeWebhookController(StripeWebhookService stripeWebhookService) {
		this.stripeWebhookService = stripeWebhookService;
	}

	@PostMapping("/stripe")
	public ResponseEntity<Void> stripe(
			@RequestBody String payload,
			@RequestHeader(name = "Stripe-Signature", required = false) String signature
	) {
		stripeWebhookService.handle(payload, signature);
		return ResponseEntity.ok().build();
	}
}
