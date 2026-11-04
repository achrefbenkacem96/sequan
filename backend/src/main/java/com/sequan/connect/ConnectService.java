package com.sequan.connect;

import com.sequan.audit.AuditService;
import com.sequan.common.error.DomainException;
import com.sequan.common.error.ErrorCode;
import com.sequan.config.SequanProperties;
import com.sequan.payment.StripeGateway;
import com.sequan.security.UserPrincipal;
import com.sequan.user.domain.UserEntity;
import com.sequan.user.domain.UserRepository;
import com.stripe.model.Account;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
public class ConnectService {

	private final UserRepository userRepository;
	private final StripeGateway stripeGateway;
	private final SequanProperties properties;
	private final AuditService auditService;

	public ConnectService(
			UserRepository userRepository,
			StripeGateway stripeGateway,
			SequanProperties properties,
			AuditService auditService
	) {
		this.userRepository = userRepository;
		this.stripeGateway = stripeGateway;
		this.properties = properties;
		this.auditService = auditService;
	}

	@Transactional
	public OnboardingLinkResponse createOnboardingLink(UserPrincipal principal) {
		UserEntity user = userRepository.findById(principal.getId())
				.orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, "User not found"));
		if (user.getStripeConnectAccountId() == null) {
			String accountId = stripeGateway.createConnectAccount(user.getEmail());
			user.setStripeConnectAccountId(accountId);
		}
		String refresh = properties.app().baseUrl() + "/connect/refresh";
		String returnUrl = properties.app().baseUrl() + "/connect/return";
		String url = stripeGateway.createAccountLink(user.getStripeConnectAccountId(), refresh, returnUrl);
		auditService.record(null, user.getId(), "CONNECT_ONBOARDING_STARTED", "User", user.getId(),
				Map.of("accountId", user.getStripeConnectAccountId()));
		return new OnboardingLinkResponse(url, user.getStripeConnectAccountId());
	}

	@Transactional
	public ConnectStatusResponse refreshStatus(UserPrincipal principal) {
		UserEntity user = userRepository.findById(principal.getId())
				.orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, "User not found"));
		if (user.getStripeConnectAccountId() == null) {
			return new ConnectStatusResponse(false, null);
		}
		Account account = stripeGateway.retrieveAccount(user.getStripeConnectAccountId());
		boolean complete = Boolean.TRUE.equals(account.getChargesEnabled())
				|| Boolean.TRUE.equals(account.getPayoutsEnabled())
				|| (account.getDetailsSubmitted() != null && account.getDetailsSubmitted());
		user.setConnectOnboardingComplete(complete);
		if (complete) {
			auditService.record(null, user.getId(), "CONNECT_ONBOARDING_COMPLETE", "User", user.getId(), null);
		}
		return new ConnectStatusResponse(complete, user.getStripeConnectAccountId());
	}

	public record OnboardingLinkResponse(String url, String accountId) {
	}

	public record ConnectStatusResponse(boolean complete, String accountId) {
	}
}
