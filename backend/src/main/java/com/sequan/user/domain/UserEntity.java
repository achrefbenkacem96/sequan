package com.sequan.user.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users")
public class UserEntity {

	@Id
	private UUID id;

	@Column(nullable = false, unique = true, length = 320)
	private String email;

	@Column(name = "password_hash", nullable = false)
	private String passwordHash;

	@Column(name = "full_name", nullable = false, length = 200)
	private String fullName;

	@Enumerated(EnumType.STRING)
	@Column(name = "global_role", nullable = false, length = 32)
	private GlobalRole globalRole;

	@Column(name = "stripe_customer_id")
	private String stripeCustomerId;

	@Column(name = "stripe_connect_account_id")
	private String stripeConnectAccountId;

	@Column(name = "connect_onboarding_complete", nullable = false)
	private boolean connectOnboardingComplete;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@Column(name = "deleted_at")
	private Instant deletedAt;

	protected UserEntity() {
	}

	public UserEntity(UUID id, String email, String passwordHash, String fullName, GlobalRole globalRole) {
		Instant now = Instant.now();
		this.id = id;
		this.email = email.toLowerCase();
		this.passwordHash = passwordHash;
		this.fullName = fullName;
		this.globalRole = globalRole;
		this.connectOnboardingComplete = false;
		this.createdAt = now;
		this.updatedAt = now;
	}

	public UUID getId() {
		return id;
	}

	public String getEmail() {
		return email;
	}

	public String getPasswordHash() {
		return passwordHash;
	}

	public String getFullName() {
		return fullName;
	}

	public GlobalRole getGlobalRole() {
		return globalRole;
	}

	public String getStripeCustomerId() {
		return stripeCustomerId;
	}

	public void setStripeCustomerId(String stripeCustomerId) {
		this.stripeCustomerId = stripeCustomerId;
		touch();
	}

	public String getStripeConnectAccountId() {
		return stripeConnectAccountId;
	}

	public void setStripeConnectAccountId(String stripeConnectAccountId) {
		this.stripeConnectAccountId = stripeConnectAccountId;
		touch();
	}

	public boolean isConnectOnboardingComplete() {
		return connectOnboardingComplete;
	}

	public void setConnectOnboardingComplete(boolean connectOnboardingComplete) {
		this.connectOnboardingComplete = connectOnboardingComplete;
		touch();
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

	public Instant getDeletedAt() {
		return deletedAt;
	}

	private void touch() {
		this.updatedAt = Instant.now();
	}
}
