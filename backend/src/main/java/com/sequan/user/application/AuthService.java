package com.sequan.user.application;

import com.sequan.audit.AuditService;
import com.sequan.common.error.DomainException;
import com.sequan.common.error.ErrorCode;
import com.sequan.config.SequanProperties;
import com.sequan.security.JwtService;
import com.sequan.security.UserPrincipal;
import com.sequan.user.api.AuthDtos;
import com.sequan.user.domain.GlobalRole;
import com.sequan.user.domain.RefreshTokenEntity;
import com.sequan.user.domain.RefreshTokenRepository;
import com.sequan.user.domain.UserEntity;
import com.sequan.user.domain.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

@Service
public class AuthService {

	private final UserRepository userRepository;
	private final RefreshTokenRepository refreshTokenRepository;
	private final PasswordEncoder passwordEncoder;
	private final JwtService jwtService;
	private final SequanProperties properties;
	private final AuditService auditService;
	private final SecureRandom secureRandom = new SecureRandom();

	public AuthService(
			UserRepository userRepository,
			RefreshTokenRepository refreshTokenRepository,
			PasswordEncoder passwordEncoder,
			JwtService jwtService,
			SequanProperties properties,
			AuditService auditService
	) {
		this.userRepository = userRepository;
		this.refreshTokenRepository = refreshTokenRepository;
		this.passwordEncoder = passwordEncoder;
		this.jwtService = jwtService;
		this.properties = properties;
		this.auditService = auditService;
	}

	@Transactional
	public AuthDtos.AuthResponse register(AuthDtos.RegisterRequest request) {
		if (userRepository.existsByEmailIgnoreCase(request.email())) {
			throw new DomainException(ErrorCode.CONFLICT, HttpStatus.CONFLICT, "Email already registered");
		}
		UserEntity user = new UserEntity(
				UUID.randomUUID(),
				request.email(),
				passwordEncoder.encode(request.password()),
				request.fullName(),
				GlobalRole.USER
		);
		userRepository.save(user);
		auditService.record(null, user.getId(), "USER_REGISTERED", "User", user.getId(), null);
		return issueTokens(user);
	}

	@Transactional
	public AuthDtos.AuthResponse login(AuthDtos.LoginRequest request) {
		UserEntity user = userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(request.email())
				.orElseThrow(() -> new DomainException(ErrorCode.UNAUTHORIZED, HttpStatus.UNAUTHORIZED, "Invalid credentials"));
		if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
			throw new DomainException(ErrorCode.UNAUTHORIZED, HttpStatus.UNAUTHORIZED, "Invalid credentials");
		}
		auditService.record(null, user.getId(), "USER_LOGIN", "User", user.getId(), null);
		return issueTokens(user);
	}

	@Transactional
	public AuthDtos.AuthResponse refresh(AuthDtos.RefreshRequest request) {
		String hash = hashToken(request.refreshToken());
		RefreshTokenEntity token = refreshTokenRepository.findByTokenHash(hash)
				.orElseThrow(() -> new DomainException(ErrorCode.UNAUTHORIZED, HttpStatus.UNAUTHORIZED, "Invalid refresh token"));
		if (!token.isActive()) {
			throw new DomainException(ErrorCode.UNAUTHORIZED, HttpStatus.UNAUTHORIZED, "Refresh token expired or revoked");
		}
		token.revoke();
		UserEntity user = userRepository.findById(token.getUserId())
				.orElseThrow(() -> new DomainException(ErrorCode.UNAUTHORIZED, HttpStatus.UNAUTHORIZED, "User not found"));
		return issueTokens(user);
	}

	@Transactional
	public void logout(String refreshToken) {
		if (refreshToken == null || refreshToken.isBlank()) {
			return;
		}
		refreshTokenRepository.findByTokenHash(hashToken(refreshToken)).ifPresent(RefreshTokenEntity::revoke);
	}

	@Transactional(readOnly = true)
	public AuthDtos.UserResponse me(UserPrincipal principal) {
		UserEntity user = userRepository.findById(principal.getId())
				.orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, "User not found"));
		return toUserResponse(user);
	}

	private AuthDtos.AuthResponse issueTokens(UserEntity user) {
		UserPrincipal principal = new UserPrincipal(user);
		String accessToken = jwtService.createAccessToken(principal);
		String refreshToken = generateRefreshToken();
		RefreshTokenEntity entity = new RefreshTokenEntity(
				UUID.randomUUID(),
				user.getId(),
				hashToken(refreshToken),
				Instant.now().plusMillis(properties.jwt().refreshExpirationMs())
		);
		refreshTokenRepository.save(entity);
		return new AuthDtos.AuthResponse(
				accessToken,
				refreshToken,
				properties.jwt().accessExpirationMs(),
				toUserResponse(user)
		);
	}

	private AuthDtos.UserResponse toUserResponse(UserEntity user) {
		return new AuthDtos.UserResponse(
				user.getId(),
				user.getEmail(),
				user.getFullName(),
				user.getGlobalRole().name(),
				user.isConnectOnboardingComplete()
		);
	}

	private String generateRefreshToken() {
		byte[] bytes = new byte[32];
		secureRandom.nextBytes(bytes);
		return HexFormat.of().formatHex(bytes);
	}

	private String hashToken(String token) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			byte[] hashed = digest.digest(token.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(hashed);
		} catch (Exception e) {
			throw new IllegalStateException("Unable to hash token", e);
		}
	}
}
