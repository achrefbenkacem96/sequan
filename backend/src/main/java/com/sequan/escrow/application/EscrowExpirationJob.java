package com.sequan.escrow.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class EscrowExpirationJob {

	private static final Logger log = LoggerFactory.getLogger(EscrowExpirationJob.class);

	private final EscrowService escrowService;

	public EscrowExpirationJob(EscrowService escrowService) {
		this.escrowService = escrowService;
	}

	@Scheduled(fixedDelayString = "${sequan.escrow.expiration-poll-ms:60000}")
	public void expireOverdueFunding() {
		int expired = escrowService.expireOverdueFunding();
		if (expired > 0) {
			log.info("Expired {} escrow(s) past funding deadline", expired);
		}
	}
}
