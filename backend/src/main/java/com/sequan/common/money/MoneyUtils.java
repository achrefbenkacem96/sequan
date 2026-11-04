package com.sequan.common.money;

import com.sequan.common.error.DomainException;
import com.sequan.common.error.ErrorCode;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;
import java.util.Locale;

public final class MoneyUtils {

	private MoneyUtils() {
	}

	public static long toMinorUnits(String decimalAmount, String currencyCode) {
		Currency currency = Currency.getInstance(currencyCode.toUpperCase(Locale.ROOT));
		int scale = currency.getDefaultFractionDigits();
		try {
			BigDecimal value = new BigDecimal(decimalAmount).setScale(scale, RoundingMode.UNNECESSARY);
			return value.movePointRight(scale).longValueExact();
		} catch (ArithmeticException | NumberFormatException ex) {
			throw new DomainException(ErrorCode.VALIDATION_ERROR, HttpStatus.BAD_REQUEST,
					"Invalid monetary amount for currency " + currencyCode);
		}
	}

	public static String toDecimalString(long minorUnits, String currencyCode) {
		Currency currency = Currency.getInstance(currencyCode.toUpperCase(Locale.ROOT));
		int scale = currency.getDefaultFractionDigits();
		return BigDecimal.valueOf(minorUnits, scale).toPlainString();
	}

	public static void assertSameCurrency(String expected, String actual) {
		if (!expected.equalsIgnoreCase(actual)) {
			throw new DomainException(ErrorCode.FINANCIAL_INVARIANT_VIOLATION, HttpStatus.CONFLICT,
					"Currency mismatch: expected " + expected + " but got " + actual);
		}
	}
}
