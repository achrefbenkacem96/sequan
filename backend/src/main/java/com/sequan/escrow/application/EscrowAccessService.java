package com.sequan.escrow.application;

import com.sequan.common.error.DomainException;
import com.sequan.common.error.ErrorCode;
import com.sequan.escrow.domain.EscrowEntity;
import com.sequan.escrow.domain.EscrowParticipantEntity;
import com.sequan.escrow.domain.EscrowParticipantRepository;
import com.sequan.escrow.domain.EscrowRepository;
import com.sequan.escrow.domain.EscrowStatus;
import com.sequan.escrow.domain.ParticipantRole;
import com.sequan.security.UserPrincipal;
import com.sequan.user.domain.GlobalRole;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class EscrowAccessService {

	private final EscrowRepository escrowRepository;
	private final EscrowParticipantRepository participantRepository;

	public EscrowAccessService(EscrowRepository escrowRepository, EscrowParticipantRepository participantRepository) {
		this.escrowRepository = escrowRepository;
		this.participantRepository = participantRepository;
	}

	@Transactional(readOnly = true)
	public EscrowEntity requireEscrow(UUID escrowId) {
		return escrowRepository.findById(escrowId)
				.orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, "Escrow not found"));
	}

	@Transactional(readOnly = true)
	public EscrowEntity requireAccessible(UUID escrowId, UserPrincipal user) {
		EscrowEntity escrow = requireEscrow(escrowId);
		if (user.getGlobalRole() == GlobalRole.ADMIN || escrow.getCreatedBy().equals(user.getId())) {
			return escrow;
		}
		boolean isParticipant = participantRepository.existsByEscrowIdAndUserId(escrowId, user.getId());
		// Drafts stay private to the creator until publish.
		if (escrow.getStatus() == EscrowStatus.DRAFT) {
			throw new DomainException(ErrorCode.ESCROW_ACCESS_DENIED, HttpStatus.FORBIDDEN,
					"This draft escrow is only visible to its creator");
		}
		// After publish (including cancelled after a decline), participants can still view
		// so they see Declined / Cancelled status in Participants.
		if (!isParticipant) {
			throw new DomainException(ErrorCode.ESCROW_ACCESS_DENIED, HttpStatus.FORBIDDEN,
					"You are not a participant of this escrow");
		}
		return escrow;
	}

	@Transactional(readOnly = true)
	public EscrowParticipantEntity requireParticipant(UUID escrowId, UUID userId) {
		return participantRepository.findByEscrowIdAndUserId(escrowId, userId)
				.orElseThrow(() -> new DomainException(ErrorCode.ESCROW_ACCESS_DENIED, HttpStatus.FORBIDDEN,
						"You are not a participant of this escrow"));
	}

	@Transactional(readOnly = true)
	public void requireRole(UUID escrowId, UserPrincipal user, ParticipantRole... roles) {
		if (user.getGlobalRole() == GlobalRole.ADMIN) {
			return;
		}
		EscrowParticipantEntity participant = requireParticipant(escrowId, user.getId());
		for (ParticipantRole role : roles) {
			if (participant.getRole() == role) {
				return;
			}
		}
		throw new DomainException(ErrorCode.FORBIDDEN, HttpStatus.FORBIDDEN,
				"Your role does not allow this action");
	}

	@Transactional(readOnly = true)
	public List<EscrowParticipantEntity> participants(UUID escrowId) {
		return participantRepository.findByEscrowId(escrowId);
	}
}
