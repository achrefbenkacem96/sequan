package com.sequan.common.money;

import com.sequan.common.error.DomainException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MoneyUtilsTest {

	@Test
	void convertsDecimalToMinorUnits() {
		assertEquals(100050, MoneyUtils.toMinorUnits("1000.50", "USD"));
		assertEquals("1000.50", MoneyUtils.toDecimalString(100050, "USD"));
	}

	@Test
	void rejectsInvalidScale() {
		assertThrows(DomainException.class, () -> MoneyUtils.toMinorUnits("10.999", "USD"));
	}

	@Test
	void detectsCurrencyMismatch() {
		assertThrows(DomainException.class, () -> MoneyUtils.assertSameCurrency("USD", "EUR"));
	}
}
