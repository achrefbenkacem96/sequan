package com.sequan.escrow.domain;

import com.sequan.common.error.DomainException;
import com.sequan.common.error.ErrorCode;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

@Component
public class EscrowStateMachine {

	private final Map<EscrowStatus, Set<EscrowStatus>> transitions = new EnumMap<>(EscrowStatus.class);

	public EscrowStateMachine() {
		transitions.put(EscrowStatus.DRAFT, EnumSet.of(EscrowStatus.PENDING_PARTICIPANTS, EscrowStatus.CANCELLED));
		transitions.put(EscrowStatus.PENDING_PARTICIPANTS, EnumSet.of(EscrowStatus.PENDING_FUNDING, EscrowStatus.CANCELLED));
		transitions.put(EscrowStatus.PENDING_FUNDING, EnumSet.of(EscrowStatus.FUNDED, EscrowStatus.CANCELLED, EscrowStatus.EXPIRED));
		transitions.put(EscrowStatus.FUNDED, EnumSet.of(
				EscrowStatus.IN_PROGRESS, EscrowStatus.AWAITING_RELEASE, EscrowStatus.REFUNDED, EscrowStatus.DISPUTED));
		transitions.put(EscrowStatus.IN_PROGRESS, EnumSet.of(
				EscrowStatus.AWAITING_RELEASE, EscrowStatus.REFUNDED, EscrowStatus.DISPUTED));
		transitions.put(EscrowStatus.AWAITING_RELEASE, EnumSet.of(
				EscrowStatus.IN_PROGRESS,
				EscrowStatus.PARTIALLY_RELEASED,
				EscrowStatus.RELEASED,
				EscrowStatus.REFUNDED,
				EscrowStatus.DISPUTED));
		transitions.put(EscrowStatus.PARTIALLY_RELEASED, EnumSet.of(
				EscrowStatus.PARTIALLY_RELEASED, EscrowStatus.RELEASED, EscrowStatus.REFUNDED, EscrowStatus.DISPUTED));
		transitions.put(EscrowStatus.DISPUTED, EnumSet.of(
				EscrowStatus.AWAITING_RELEASE,
				EscrowStatus.RELEASED,
				EscrowStatus.REFUNDED,
				EscrowStatus.CANCELLED,
				EscrowStatus.PARTIALLY_RELEASED));
		transitions.put(EscrowStatus.RELEASED, EnumSet.noneOf(EscrowStatus.class));
		transitions.put(EscrowStatus.REFUNDED, EnumSet.noneOf(EscrowStatus.class));
		transitions.put(EscrowStatus.CANCELLED, EnumSet.noneOf(EscrowStatus.class));
		transitions.put(EscrowStatus.EXPIRED, EnumSet.noneOf(EscrowStatus.class));
	}

	public void assertTransition(EscrowStatus from, EscrowStatus to) {
		Set<EscrowStatus> allowed = transitions.getOrDefault(from, EnumSet.noneOf(EscrowStatus.class));
		if (!allowed.contains(to)) {
			throw new DomainException(
					ErrorCode.ESCROW_INVALID_STATE,
					HttpStatus.CONFLICT,
					"The escrow cannot transition from " + from + " to " + to
			);
		}
	}

	public boolean canTransition(EscrowStatus from, EscrowStatus to) {
		return transitions.getOrDefault(from, EnumSet.noneOf(EscrowStatus.class)).contains(to);
	}
}
