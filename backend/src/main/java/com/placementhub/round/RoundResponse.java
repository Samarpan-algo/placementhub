package com.placementhub.round;

import java.time.Instant;

public record RoundResponse(
		Long id,
		Long driveId,
		int roundNo,
		String name,
		Instant scheduledAt) {

	/** Only reads the drive's id, which a LAZY proxy has without a query. */
	public static RoundResponse from(DriveRound round) {
		return new RoundResponse(round.getId(), round.getDrive().getId(), round.getRoundNo(), round.getName(),
				round.getScheduledAt());
	}

}
