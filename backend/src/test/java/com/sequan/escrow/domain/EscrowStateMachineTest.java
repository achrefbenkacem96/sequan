package com.sequan.escrow.domain;

import com.sequan.common.error.DomainException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EscrowStateMachineTest {

	private final EscrowStateMachine stateMachine = new EscrowStateMachine();

	@Test
	void allowsPublishAndFundingPath() {
		assertDoesNotThrow(() -> stateMachine.assertTransition(EscrowStatus.DRAFT, EscrowStatus.PENDING_PARTICIPANTS));
		assertDoesNotThrow(() -> stateMachine.assertTransition(EscrowStatus.PENDING_PARTICIPANTS, EscrowStatus.PENDING_FUNDING));
		assertDoesNotThrow(() -> stateMachine.assertTransition(EscrowStatus.PENDING_FUNDING, EscrowStatus.FUNDED));
		assertDoesNotThrow(() -> stateMachine.assertTransition(EscrowStatus.FUNDED, EscrowStatus.IN_PROGRESS));
		assertDoesNotThrow(() -> stateMachine.assertTransition(EscrowStatus.IN_PROGRESS, EscrowStatus.AWAITING_RELEASE));
		assertDoesNotThrow(() -> stateMachine.assertTransition(EscrowStatus.AWAITING_RELEASE, EscrowStatus.RELEASED));
	}

	@Test
	void allowsDisputeReleaseResolutionPath() {
		assertDoesNotThrow(() -> stateMachine.assertTransition(EscrowStatus.FUNDED, EscrowStatus.DISPUTED));
		assertDoesNotThrow(() -> stateMachine.assertTransition(EscrowStatus.DISPUTED, EscrowStatus.AWAITING_RELEASE));
		assertDoesNotThrow(() -> stateMachine.assertTransition(EscrowStatus.AWAITING_RELEASE, EscrowStatus.IN_PROGRESS));
	}

	@Test
	void allowsExpireFromPendingFunding() {
		assertDoesNotThrow(() -> stateMachine.assertTransition(EscrowStatus.PENDING_FUNDING, EscrowStatus.EXPIRED));
	}

	@Test
	void rejectsArbitraryReleaseFromDraft() {
		DomainException ex = assertThrows(DomainException.class,
				() -> stateMachine.assertTransition(EscrowStatus.DRAFT, EscrowStatus.RELEASED));
		assertTrue(ex.getMessage().contains("cannot transition"));
	}

	@Test
	void rejectsTransitionFromTerminalState() {
		assertThrows(DomainException.class,
				() -> stateMachine.assertTransition(EscrowStatus.RELEASED, EscrowStatus.FUNDED));
	}
}
