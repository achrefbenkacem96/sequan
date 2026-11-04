package com.sequan.connect;

import com.sequan.security.SecurityUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/connect")
public class ConnectController {

	private final ConnectService connectService;

	public ConnectController(ConnectService connectService) {
		this.connectService = connectService;
	}

	@PostMapping("/onboarding-link")
	public ConnectService.OnboardingLinkResponse onboardingLink() {
		return connectService.createOnboardingLink(SecurityUtils.requireCurrentUser());
	}

	@GetMapping("/status")
	public ConnectService.ConnectStatusResponse status() {
		return connectService.refreshStatus(SecurityUtils.requireCurrentUser());
	}
}
