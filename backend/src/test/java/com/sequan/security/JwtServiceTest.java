package com.sequan.security;

import com.sequan.config.SequanProperties;
import com.sequan.user.domain.GlobalRole;
import com.sequan.user.domain.UserEntity;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JwtServiceTest {

	@Test
	void roundTripsUserId() {
		SequanProperties properties = new SequanProperties(
				new SequanProperties.Jwt("test-secret-key-that-is-long-enough-for-hs256", 900000, 604800000),
				new SequanProperties.Cors("http://localhost:4200"),
				new SequanProperties.App("http://localhost:4200", "http://localhost:8080"),
				new SequanProperties.Stripe("sk_test", "pk_test", "whsec_test")
		);
		JwtService jwtService = new JwtService(properties);
		UserEntity user = new UserEntity(UUID.randomUUID(), "a@example.com", "hash", "Alice", GlobalRole.USER);
		UserPrincipal principal = new UserPrincipal(user);

		String token = jwtService.createAccessToken(principal);
		assertEquals(user.getId(), jwtService.parseUserId(token));
	}
}
