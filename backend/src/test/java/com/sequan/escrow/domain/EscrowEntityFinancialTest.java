package com.sequan.escrow.domain;

import com.sequan.common.error.DomainException;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EscrowEntityFinancialTest {

	@Test
	void fundingReleaseRefundMaintainInvariant() {
		EscrowEntity escrow = new EscrowEntity(
				UUID.randomUUID(),
				"Test",
				"desc",
				"USD",
				10_000,
				UUID.randomUUID(),
				null,
				null
		);
		escrow.markFunded(10_000);
		assertEquals(10_000, escrow.getAmountAvailable());

		escrow.applyRelease(4_000);
		assertEquals(6_000, escrow.getAmountAvailable());
		assertEquals(4_000, escrow.getAmountReleased());

		escrow.applyRefund(6_000);
		assertEquals(0, escrow.getAmountAvailable());
		assertEquals(6_000, escrow.getAmountRefunded());
		assertEquals(escrow.getAmountFunded(),
				escrow.getAmountReleased() + escrow.getAmountRefunded() + escrow.getAmountAvailable());
	}

	@Test
	void preventsOverRelease() {
		EscrowEntity escrow = new EscrowEntity(
				UUID.randomUUID(),
				"Test",
				"desc",
				"USD",
				1_000,
				UUID.randomUUID(),
				null,
				null
		);
		escrow.markFunded(1_000);
		assertThrows(DomainException.class, () -> escrow.applyRelease(1_001));
	}

	@Test
	void preventsOverRefund() {
		EscrowEntity escrow = new EscrowEntity(
				UUID.randomUUID(),
				"Test",
				"desc",
				"USD",
				1_000,
				UUID.randomUUID(),
				null,
				null
		);
		escrow.markFunded(1_000);
		escrow.applyRelease(700);
		assertThrows(DomainException.class, () -> escrow.applyRefund(400));
	}

	@Test
	void reverseReleaseRestoresBalance() {
		EscrowEntity escrow = new EscrowEntity(
				UUID.randomUUID(),
				"Test",
				"desc",
				"USD",
				1_000,
				UUID.randomUUID(),
				null,
				null
		);
		escrow.markFunded(1_000);
		escrow.applyRelease(400);
		escrow.reverseRelease(400);
		assertEquals(1_000, escrow.getAmountAvailable());
		assertEquals(0, escrow.getAmountReleased());
	}

	@Test
	void reverseRefundRestoresBalance() {
		EscrowEntity escrow = new EscrowEntity(
				UUID.randomUUID(),
				"Test",
				"desc",
				"USD",
				1_000,
				UUID.randomUUID(),
				null,
				null
		);
		escrow.markFunded(1_000);
		escrow.applyRefund(250);
		escrow.reverseRefund(250);
		assertEquals(1_000, escrow.getAmountAvailable());
		assertEquals(0, escrow.getAmountRefunded());
	}
}
