package com.placementhub.round;

import java.util.Collection;
import java.util.Comparator;
import java.util.Optional;

import com.placementhub.application.ApplicationStatus;

/**
 * Pure logic: an application's status follows from all its round results, so a correction to
 * any round gives the right status without remembering what happened before.
 * <ol>
 *   <li>Any round NOT_CLEARED or ABSENT → REJECTED, "Not cleared in Round N: name" (the earliest such round)</li>
 *   <li>Otherwise at least one round CLEARED → SHORTLISTED (no reason)</li>
 *   <li>No results at all → APPLIED (no reason)</li>
 * </ol>
 * Only call it for applications whose status comes from rounds (not SELECTED, WITHDRAWN or INELIGIBLE).
 */
public final class RoundStatusCalculator {

	public record StatusDecision(ApplicationStatus status, String reason) {
	}

	private RoundStatusCalculator() {
	}

	/** @param results every round result of one application (any order; rounds must be loaded) */
	public static StatusDecision statusFrom(Collection<RoundResult> results) {
		Optional<RoundResult> firstFailure = results.stream()
				.filter(r -> !r.getResult().cleared())
				.min(Comparator.comparing(r -> r.getRound().getRoundNo()));
		if (firstFailure.isPresent()) {
			return new StatusDecision(ApplicationStatus.REJECTED,
					"Not cleared in " + firstFailure.get().getRound().label());
		}
		if (!results.isEmpty()) {
			return new StatusDecision(ApplicationStatus.SHORTLISTED, null);
		}
		return new StatusDecision(ApplicationStatus.APPLIED, null);
	}

}
