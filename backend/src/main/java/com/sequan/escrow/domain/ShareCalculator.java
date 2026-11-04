package com.sequan.escrow.domain;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Computes each payee's entitled share of an escrow total.
 * Percent shares use floor division; any leftover cents go to the first payee
 * (stable order by participant id) so the sum always equals {@code amountTotal}.
 */
public final class ShareCalculator {

	private ShareCalculator() {
	}

	public static long entitledAmount(
			EscrowParticipantEntity payee,
			long amountTotal,
			List<EscrowParticipantEntity> allPayees
	) {
		List<EscrowParticipantEntity> payees = payeesOf(allPayees);
		if (payees.isEmpty()) {
			return 0;
		}
		if (payee.getShareType() == ShareType.FIXED_MINOR) {
			return payee.getShareValue();
		}
		return percentEntitlements(amountTotal, payees).stream()
				.filter(e -> e.participantId().equals(payee.getId()))
				.mapToLong(Entitlement::amount)
				.findFirst()
				.orElse(0L);
	}

	public static List<Entitlement> percentEntitlements(long amountTotal, List<EscrowParticipantEntity> payees) {
		List<EscrowParticipantEntity> ordered = new ArrayList<>(payees);
		ordered.sort(Comparator.comparing(EscrowParticipantEntity::getId));
		List<Entitlement> entitlements = new ArrayList<>();
		long allocated = 0;
		for (int i = 0; i < ordered.size(); i++) {
			EscrowParticipantEntity p = ordered.get(i);
			long amount;
			if (i == ordered.size() - 1) {
				amount = amountTotal - allocated;
			} else {
				amount = Math.multiplyExact(amountTotal, p.getShareValue()) / 10_000L;
				allocated += amount;
			}
			entitlements.add(new Entitlement(p.getId(), amount));
		}
		return entitlements;
	}

	public static List<EscrowParticipantEntity> payeesOf(List<EscrowParticipantEntity> participants) {
		return participants.stream()
				.filter(p -> p.getRole() == ParticipantRole.PROVIDER || p.getRole() == ParticipantRole.BENEFICIARY)
				.toList();
	}

	public record Entitlement(UUID participantId, long amount) {
	}
}
