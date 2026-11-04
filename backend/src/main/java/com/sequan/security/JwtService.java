package com.sequan.security;

import com.sequan.config.SequanProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

@Service
public class JwtService {

	private final SecretKey key;
	private final long accessExpirationMs;

	public JwtService(SequanProperties properties) {
		byte[] secretBytes = properties.jwt().secret().getBytes(StandardCharsets.UTF_8);
		this.key = Keys.hmacShaKeyFor(secretBytes);
		this.accessExpirationMs = properties.jwt().accessExpirationMs();
	}

	public String createAccessToken(UserPrincipal principal) {
		Instant now = Instant.now();
		return Jwts.builder()
				.subject(principal.getId().toString())
				.claim("email", principal.getUsername())
				.claim("role", principal.getGlobalRole().name())
				.issuedAt(Date.from(now))
				.expiration(Date.from(now.plusMillis(accessExpirationMs)))
				.signWith(key)
				.compact();
	}

	public UUID parseUserId(String token) {
		Claims claims = Jwts.parser()
				.verifyWith(key)
				.build()
				.parseSignedClaims(token)
				.getPayload();
		return UUID.fromString(claims.getSubject());
	}
}
