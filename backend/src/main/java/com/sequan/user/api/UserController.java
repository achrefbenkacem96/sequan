package com.sequan.user.api;

import com.sequan.common.error.DomainException;
import com.sequan.common.error.ErrorCode;
import com.sequan.user.domain.UserEntity;
import com.sequan.user.domain.UserRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/users")
public class UserController {

	private final UserRepository userRepository;

	public UserController(UserRepository userRepository) {
		this.userRepository = userRepository;
	}

	@GetMapping("/lookup")
	public AuthDtos.UserResponse lookup(@RequestParam String email) {
		UserEntity user = userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(email)
				.orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, "User not found"));
		return toResponse(user);
	}

	@GetMapping("/search")
	public List<AuthDtos.UserResponse> search(@RequestParam String q) {
		String query = q == null ? "" : q.trim();
		if (query.length() < 2) {
			return List.of();
		}
		return userRepository.searchActive(query, PageRequest.of(0, 20)).stream()
				.map(this::toResponse)
				.toList();
	}

	private AuthDtos.UserResponse toResponse(UserEntity user) {
		return new AuthDtos.UserResponse(
				user.getId(),
				user.getEmail(),
				user.getFullName(),
				user.getGlobalRole().name(),
				user.isConnectOnboardingComplete()
		);
	}
}
