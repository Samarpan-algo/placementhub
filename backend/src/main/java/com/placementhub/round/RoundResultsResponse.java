package com.placementhub.round;

import java.util.List;

import com.placementhub.application.ApplicationStatus;

/** What was saved, with each application's status after recalculation. */
public record RoundResultsResponse(
		Long roundId,
		int roundNo,
		String roundName,
		List<Item> results) {

	public record Item(
			Long applicationId,
			String studentName,
			String rollNo,
			RoundOutcome result,
			String remarks,
			ApplicationStatus applicationStatus,
			String statusReason) {
	}

}
