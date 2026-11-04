package com.sequan.user.api;

import com.sequan.security.SecurityUtils;
import com.sequan.user.application.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

	private final AuthService authService;

	public AuthController(AuthService authService) {
		this.authService = authService;
	}

	@PostMapping("/register")
	@ResponseStatus(HttpStatus.CREATED)
	public AuthDtos.AuthResponse register(@Valid @RequestBody AuthDtos.RegisterRequest request) {
		return authService.register(request);
	}

	@PostMapping("/login")
	public AuthDtos.AuthResponse login(@Valid @RequestBody AuthDtos.LoginRequest request) {
		return authService.login(request);
	}

	@PostMapping("/refresh")
	public AuthDtos.AuthResponse refresh(@Valid @RequestBody AuthDtos.RefreshRequest request) {
		return authService.refresh(request);
	}

	@PostMapping("/logout")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void logout(@RequestBody(required = false) AuthDtos.RefreshRequest request) {
		authService.logout(request == null ? null : request.refreshToken());
	}

	@GetMapping("/me")
	public AuthDtos.UserResponse me() {
		return authService.me(SecurityUtils.requireCurrentUser());
	}
}
