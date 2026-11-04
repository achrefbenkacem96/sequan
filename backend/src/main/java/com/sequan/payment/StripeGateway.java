package com.sequan.payment;

import com.sequan.common.error.DomainException;
import com.sequan.common.error.ErrorCode;
import com.sequan.config.SequanProperties;
import com.stripe.StripeClient;
import com.stripe.exception.StripeException;
import com.stripe.model.Account;
import com.stripe.model.AccountLink;
import com.stripe.model.BalanceTransaction;
import com.stripe.model.Charge;
import com.stripe.model.Customer;
import com.stripe.model.PaymentIntent;
import com.stripe.model.Refund;
import com.stripe.model.Transfer;
import com.stripe.model.checkout.Session;
import com.stripe.net.RequestOptions;
import com.stripe.param.AccountCreateParams;
import com.stripe.param.AccountLinkCreateParams;
import com.stripe.param.ChargeRetrieveParams;
import com.stripe.param.CustomerCreateParams;
import com.stripe.param.RefundCreateParams;
import com.stripe.param.TransferCreateParams;
import com.stripe.param.checkout.SessionCreateParams;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Component
public class StripeGateway {

	private final StripeClient client;
	private final SequanProperties properties;

	public StripeGateway(SequanProperties properties) {
		this.properties = properties;
		this.client = new StripeClient(properties.stripe().secretKey());
	}

	public String publishableKey() {
		return properties.stripe().publishableKey();
	}

	public String ensureCustomer(String existingCustomerId, String email, String name) {
		try {
			if (existingCustomerId != null && !existingCustomerId.isBlank()) {
				return existingCustomerId;
			}
			Customer customer = client.customers().create(CustomerCreateParams.builder()
					.setEmail(email)
					.setName(name)
					.build());
			return customer.getId();
		} catch (StripeException e) {
			throw stripeError(e);
		}
	}

	public SessionCreateResult createCheckoutSession(
			UUID escrowId,
			UUID paymentId,
			long amountMinor,
			String currency,
			String customerId,
			String returnUrl,
			String cancelUrl,
			String idempotencyKey
	) {
		try {
			Map<String, String> metadata = new HashMap<>();
			metadata.put("escrowId", escrowId.toString());
			metadata.put("paymentId", paymentId.toString());
			metadata.put("cancelUrl", cancelUrl);

			// Hosted Checkout is used instead of embedded_page: Stripe's embedded
			// iframe often stays blank in local/dev browsers after the ui_mode rename.
			SessionCreateParams params = SessionCreateParams.builder()
					.setMode(SessionCreateParams.Mode.PAYMENT)
					.setUiMode(SessionCreateParams.UiMode.HOSTED_PAGE)
					.setSuccessUrl(returnUrl)
					.setCancelUrl(cancelUrl)
					.setCustomer(customerId)
					.setClientReferenceId(paymentId.toString())
					.putAllMetadata(metadata)
					.setPaymentIntentData(SessionCreateParams.PaymentIntentData.builder()
							.setTransferGroup("escrow_" + escrowId)
							.putMetadata("escrowId", escrowId.toString())
							.putMetadata("paymentId", paymentId.toString())
							.build())
					.addLineItem(SessionCreateParams.LineItem.builder()
							.setQuantity(1L)
							.setPriceData(SessionCreateParams.LineItem.PriceData.builder()
									.setCurrency(currency.toLowerCase())
									.setUnitAmount(amountMinor)
									.setProductData(SessionCreateParams.LineItem.PriceData.ProductData.builder()
											.setName("Escrow funding")
											.setDescription("Funding for escrow " + escrowId)
											.build())
									.build())
							.build())
					.build();

			Session session = client.checkout().sessions().create(params, requestOptions(idempotencyKey));
			return new SessionCreateResult(
					session.getId(),
					session.getClientSecret(),
					session.getStatus(),
					session.getUiMode(),
					session.getUrl()
			);
		} catch (StripeException e) {
			throw stripeError(e);
		}
	}

	public String createConnectAccount(String email) {
		try {
			Account account = client.accounts().create(AccountCreateParams.builder()
					.setType(AccountCreateParams.Type.EXPRESS)
					.setEmail(email)
					.setCapabilities(AccountCreateParams.Capabilities.builder()
							.setTransfers(AccountCreateParams.Capabilities.Transfers.builder()
									.setRequested(true)
									.build())
							.build())
					.build());
			return account.getId();
		} catch (StripeException e) {
			throw stripeError(e);
		}
	}

	public String createAccountLink(String accountId, String refreshUrl, String returnUrl) {
		try {
			AccountLink link = client.accountLinks().create(AccountLinkCreateParams.builder()
					.setAccount(accountId)
					.setRefreshUrl(refreshUrl)
					.setReturnUrl(returnUrl)
					.setType(AccountLinkCreateParams.Type.ACCOUNT_ONBOARDING)
					.build());
			return link.getUrl();
		} catch (StripeException e) {
			throw stripeError(e);
		}
	}

	public Account retrieveAccount(String accountId) {
		try {
			return client.accounts().retrieve(accountId);
		} catch (StripeException e) {
			throw stripeError(e);
		}
	}

	public String createTransfer(
			long amountMinor,
			String currency,
			String destinationAccountId,
			String transferGroup,
			String sourceTransaction,
			UUID releaseId,
			UUID escrowId,
			String idempotencyKey
	) {
		try {
			TransferCreateParams.Builder builder = TransferCreateParams.builder()
					.setAmount(amountMinor)
					.setCurrency(currency.toLowerCase())
					.setDestination(destinationAccountId)
					.setTransferGroup(transferGroup)
					.putMetadata("releaseId", releaseId.toString())
					.putMetadata("escrowId", escrowId.toString());
			if (sourceTransaction != null && !sourceTransaction.isBlank()) {
				builder.setSourceTransaction(sourceTransaction);
			}
			Transfer transfer = client.transfers().create(builder.build(), requestOptions(idempotencyKey));
			return transfer.getId();
		} catch (StripeException e) {
			throw stripeError(e);
		}
	}

	public String createRefund(
			String paymentIntentId,
			long amountMinor,
			UUID refundId,
			UUID escrowId,
			String idempotencyKey
	) {
		try {
			Refund refund = client.refunds().create(RefundCreateParams.builder()
					.setPaymentIntent(paymentIntentId)
					.setAmount(amountMinor)
					.putMetadata("refundId", refundId.toString())
					.putMetadata("escrowId", escrowId.toString())
					.build(), requestOptions(idempotencyKey));
			return refund.getId();
		} catch (StripeException e) {
			throw stripeError(e);
		}
	}

	public SessionCreateResult retrieveCheckoutSession(String sessionId) {
		try {
			Session session = client.checkout().sessions().retrieve(sessionId);
			return new SessionCreateResult(
					session.getId(),
					session.getClientSecret(),
					session.getStatus(),
					session.getUiMode(),
					session.getUrl()
			);
		} catch (StripeException e) {
			throw stripeError(e);
		}
	}

	public CheckoutSessionDetails retrieveCheckoutSessionDetails(String sessionId) {
		try {
			Session session = client.checkout().sessions().retrieve(sessionId);
			String paymentId = session.getMetadata() != null ? session.getMetadata().get("paymentId") : null;
			String escrowId = session.getMetadata() != null ? session.getMetadata().get("escrowId") : null;
			return new CheckoutSessionDetails(
					session.getId(),
					session.getStatus(),
					session.getPaymentStatus(),
					session.getPaymentIntent(),
					paymentId,
					escrowId
			);
		} catch (StripeException e) {
			throw stripeError(e);
		}
	}

	/**
	 * Resolves the Charge id that funded a PaymentIntent. Required for Connect
	 * transfers with {@code source_transaction}, so releases debit that charge
	 * instead of the platform available balance.
	 */
	public String retrieveLatestChargeId(String paymentIntentId) {
		if (paymentIntentId == null || paymentIntentId.isBlank()) {
			return null;
		}
		try {
			PaymentIntent intent = client.paymentIntents().retrieve(paymentIntentId);
			String chargeId = intent.getLatestCharge();
			return chargeId == null || chargeId.isBlank() ? null : chargeId;
		} catch (StripeException e) {
			throw stripeError(e);
		}
	}

	/**
	 * Charge settlement details. Stripe platforms often settle foreign-currency
	 * charges into the account default currency (e.g. USD charge → EUR balance),
	 * and {@code source_transaction} transfers must use that settlement currency.
	 */
	public ChargeSettlement retrieveChargeSettlement(String chargeId) {
		if (chargeId == null || chargeId.isBlank()) {
			throw new DomainException(ErrorCode.VALIDATION_ERROR, HttpStatus.BAD_REQUEST, "chargeId is required");
		}
		try {
			Charge charge = client.charges().retrieve(
					chargeId,
					ChargeRetrieveParams.builder().addExpand("balance_transaction").build(),
					RequestOptions.getDefault()
			);
			String chargeCurrency = charge.getCurrency() == null ? "" : charge.getCurrency().toLowerCase();
			long chargeAmount = charge.getAmount() == null ? 0L : charge.getAmount();
			BalanceTransaction bt = charge.getBalanceTransactionObject();
			if (bt == null) {
				return new ChargeSettlement(chargeId, chargeCurrency, chargeAmount, chargeCurrency, chargeAmount, null);
			}
			String settlementCurrency = bt.getCurrency() == null ? chargeCurrency : bt.getCurrency().toLowerCase();
			long settlementAmount = bt.getAmount() == null ? chargeAmount : bt.getAmount();
			return new ChargeSettlement(
					chargeId,
					chargeCurrency,
					chargeAmount,
					settlementCurrency,
					settlementAmount,
					bt.getExchangeRate()
			);
		} catch (StripeException e) {
			throw stripeError(e);
		}
	}

	/**
	 * Maps an escrow-currency release amount onto the Stripe transfer amount/currency
	 * required when using {@code source_transaction}.
	 */
	public TransferAmount resolveTransferAmount(long releaseAmountMinor, String escrowCurrency, ChargeSettlement settlement) {
		String escrow = escrowCurrency == null ? "" : escrowCurrency.toLowerCase();
		if (settlement == null || settlement.chargeId() == null || settlement.chargeId().isBlank()) {
			return new TransferAmount(releaseAmountMinor, escrow, null);
		}
		if (settlement.settlementCurrency().equals(escrow) || settlement.chargeAmount() <= 0) {
			return new TransferAmount(releaseAmountMinor, escrow, settlement.chargeId());
		}
		long transferAmount = Math.round((double) releaseAmountMinor / settlement.chargeAmount() * settlement.settlementAmount());
		if (transferAmount <= 0 && releaseAmountMinor > 0) {
			transferAmount = 1;
		}
		if (transferAmount > settlement.settlementAmount()) {
			transferAmount = settlement.settlementAmount();
		}
		return new TransferAmount(transferAmount, settlement.settlementCurrency(), settlement.chargeId());
	}

	private RequestOptions requestOptions(String idempotencyKey) {
		if (idempotencyKey == null || idempotencyKey.isBlank()) {
			return RequestOptions.getDefault();
		}
		return RequestOptions.builder().setIdempotencyKey(idempotencyKey).build();
	}

	private DomainException stripeError(StripeException e) {
		return new DomainException(ErrorCode.STRIPE_ERROR, HttpStatus.BAD_GATEWAY,
				"Stripe request failed: " + e.getMessage());
	}

	public record SessionCreateResult(
			String sessionId,
			String clientSecret,
			String status,
			String uiMode,
			String checkoutUrl
	) {
		public boolean isReusableHosted() {
			return "open".equalsIgnoreCase(status)
					&& "hosted_page".equalsIgnoreCase(uiMode)
					&& checkoutUrl != null
					&& !checkoutUrl.isBlank();
		}
	}

	public record CheckoutSessionDetails(
			String sessionId,
			String status,
			String paymentStatus,
			String paymentIntentId,
			String paymentId,
			String escrowId
	) {
	}

	public record ChargeSettlement(
			String chargeId,
			String chargeCurrency,
			long chargeAmount,
			String settlementCurrency,
			long settlementAmount,
			java.math.BigDecimal exchangeRate
	) {
	}

	public record TransferAmount(
			long amountMinor,
			String currency,
			String sourceTransaction
	) {
	}
}
