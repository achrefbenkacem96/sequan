package com.sequan.escrow.domain;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ShareCalculatorTest {

	@Test
	void fixedShareReturnsConfiguredValue() {
		EscrowParticipantEntity payee = payee(ShareType.FIXED_MINOR, 4_000);
		long entitled = ShareCalculator.entitledAmount(payee, 10_000, List.of(payee));
		assertEquals(4_000, entitled);
	}

	@Test
	void percentSharesAllocateFullTotalWithRemainder() {
		EscrowParticipantEntity a = payee(ShareType.PERCENT_BPS, 3_333);
		EscrowParticipantEntity b = payee(ShareType.PERCENT_BPS, 6_667);
		List<EscrowParticipantEntity> payees = List.of(a, b);

		long entitledA = ShareCalculator.entitledAmount(a, 100, payees);
		long entitledB = ShareCalculator.entitledAmount(b, 100, payees);

		assertEquals(100, entitledA + entitledB);
	}

	@Test
	void singlePercentPayeeGetsFullAmount() {
		EscrowParticipantEntity payee = payee(ShareType.PERCENT_BPS, 10_000);
		assertEquals(12_345, ShareCalculator.entitledAmount(payee, 12_345, List.of(payee)));
	}

	private static EscrowParticipantEntity payee(ShareType type, long value) {
		return new EscrowParticipantEntity(
				UUID.randomUUID(),
				UUID.randomUUID(),
				UUID.randomUUID(),
				ParticipantRole.PROVIDER,
				type,
				value,
				ParticipationStatus.ACCEPTED,
				false,
				"payee@example.com"
		);
	}
}
