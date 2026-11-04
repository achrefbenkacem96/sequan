package com.sequan.escrow.domain;

import com.sequan.common.error.DomainException;
import com.sequan.common.error.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "escrows")
public class EscrowEntity {

	@Id
	private UUID id;

	@Column(nullable = false, length = 200)
	private String title;

	@Column
	private String description;

	@Column(nullable = false, length = 3)
	private String currency;

	@Column(name = "amount_total", nullable = false)
	private long amountTotal;

	@Column(name = "amount_funded", nullable = false)
	private long amountFunded;

	@Column(name = "amount_released", nullable = false)
	private long amountReleased;

	@Column(name = "amount_refunded", nullable = false)
	private long amountRefunded;

	@Column(name = "amount_available", nullable = false)
	private long amountAvailable;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 40)
	private EscrowStatus status;

	@Column(name = "funding_deadline")
	private Instant fundingDeadline;

	@Column(name = "completion_deadline")
	private Instant completionDeadline;

	@Column(name = "created_by", nullable = false)
	private UUID createdBy;

	@Version
	@Column(nullable = false)
	private long version;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected EscrowEntity() {
	}

	public EscrowEntity(
			UUID id,
			String title,
			String description,
			String currency,
			long amountTotal,
			UUID createdBy,
			Instant fundingDeadline,
			Instant completionDeadline
	) {
		if (amountTotal <= 0) {
			throw new DomainException(ErrorCode.VALIDATION_ERROR, HttpStatus.BAD_REQUEST, "Amount must be positive");
		}
		Instant now = Instant.now();
		this.id = id;
		this.title = title;
		this.description = description;
		this.currency = currency.toUpperCase();
		this.amountTotal = amountTotal;
		this.amountFunded = 0;
		this.amountReleased = 0;
		this.amountRefunded = 0;
		this.amountAvailable = 0;
		this.status = EscrowStatus.DRAFT;
		this.createdBy = createdBy;
		this.fundingDeadline = fundingDeadline;
		this.completionDeadline = completionDeadline;
		this.createdAt = now;
		this.updatedAt = now;
	}

	public void updateDraft(String title, String description, Instant fundingDeadline, Instant completionDeadline) {
		assertStatus(EscrowStatus.DRAFT);
		this.title = title;
		this.description = description;
		this.fundingDeadline = fundingDeadline;
		this.completionDeadline = completionDeadline;
		touch();
	}

	public void transitionTo(EscrowStatus next, EscrowStateMachine stateMachine) {
		stateMachine.assertTransition(this.status, next);
		this.status = next;
		touch();
	}

	public void markFunded(long amount) {
		if (amount != amountTotal) {
			throw new DomainException(ErrorCode.FINANCIAL_INVARIANT_VIOLATION, HttpStatus.CONFLICT,
					"Funded amount must equal escrow total");
		}
		this.amountFunded = amount;
		this.amountAvailable = amount;
		assertBalance();
		touch();
	}

	public void applyRelease(long amount) {
		if (amount <= 0 || amount > amountAvailable) {
			throw new DomainException(ErrorCode.INSUFFICIENT_FUNDS, HttpStatus.CONFLICT,
					"Cannot release more than available escrow balance");
		}
		this.amountAvailable -= amount;
		this.amountReleased += amount;
		assertBalance();
		touch();
	}

	public void reverseRelease(long amount) {
		if (amount <= 0 || amount > amountReleased) {
			throw new DomainException(ErrorCode.FINANCIAL_INVARIANT_VIOLATION, HttpStatus.CONFLICT,
					"Cannot reverse release for more than released amount");
		}
		this.amountReleased -= amount;
		this.amountAvailable += amount;
		assertBalance();
		touch();
	}

	public void applyRefund(long amount) {
		if (amount <= 0 || amount > amountAvailable) {
			throw new DomainException(ErrorCode.INSUFFICIENT_FUNDS, HttpStatus.CONFLICT,
					"Cannot refund more than available escrow balance");
		}
		this.amountAvailable -= amount;
		this.amountRefunded += amount;
		assertBalance();
		touch();
	}

	public void reverseRefund(long amount) {
		if (amount <= 0 || amount > amountRefunded) {
			throw new DomainException(ErrorCode.FINANCIAL_INVARIANT_VIOLATION, HttpStatus.CONFLICT,
					"Cannot reverse refund for more than refunded amount");
		}
		this.amountRefunded -= amount;
		this.amountAvailable += amount;
		assertBalance();
		touch();
	}

	public void assertStatus(EscrowStatus expected) {
		if (this.status != expected) {
			throw new DomainException(ErrorCode.ESCROW_INVALID_STATE, HttpStatus.CONFLICT,
					"Escrow must be in status " + expected + " but is " + status);
		}
	}

	public void assertOneOf(EscrowStatus... statuses) {
		for (EscrowStatus s : statuses) {
			if (this.status == s) {
				return;
			}
		}
		throw new DomainException(ErrorCode.ESCROW_INVALID_STATE, HttpStatus.CONFLICT,
				"Escrow status " + status + " does not allow this operation");
	}

	private void assertBalance() {
		if (amountFunded != amountReleased + amountRefunded + amountAvailable) {
			throw new DomainException(ErrorCode.FINANCIAL_INVARIANT_VIOLATION, HttpStatus.CONFLICT,
					"Escrow balance invariant violated");
		}
		if (amountFunded < 0 || amountReleased < 0 || amountRefunded < 0 || amountAvailable < 0) {
			throw new DomainException(ErrorCode.FINANCIAL_INVARIANT_VIOLATION, HttpStatus.CONFLICT,
					"Negative monetary amounts are not allowed");
		}
	}

	private void touch() {
		this.updatedAt = Instant.now();
	}

	public UUID getId() {
		return id;
	}

	public String getTitle() {
		return title;
	}

	public String getDescription() {
		return description;
	}

	public String getCurrency() {
		return currency;
	}

	public long getAmountTotal() {
		return amountTotal;
	}

	public long getAmountFunded() {
		return amountFunded;
	}

	public long getAmountReleased() {
		return amountReleased;
	}

	public long getAmountRefunded() {
		return amountRefunded;
	}

	public long getAmountAvailable() {
		return amountAvailable;
	}

	public EscrowStatus getStatus() {
		return status;
	}

	public Instant getFundingDeadline() {
		return fundingDeadline;
	}

	public Instant getCompletionDeadline() {
		return completionDeadline;
	}

	public UUID getCreatedBy() {
		return createdBy;
	}

	public long getVersion() {
		return version;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}
}
