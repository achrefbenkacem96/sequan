package com.sequan.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "sequan")
public record SequanProperties(
		Jwt jwt,
		Cors cors,
		App app,
		Stripe stripe
) {
	public record Jwt(String secret, long accessExpirationMs, long refreshExpirationMs) {
	}

	public record Cors(String allowedOrigins) {
	}

	public record App(String baseUrl, String apiBaseUrl) {
	}

	public record Stripe(String secretKey, String publishableKey, String webhookSecret) {
	}
}
