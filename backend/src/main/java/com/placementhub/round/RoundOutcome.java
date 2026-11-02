package com.placementhub.round;

/** A student's result in one round. Must match ck_round_results_result in the DB. */
public enum RoundOutcome {
	CLEARED,
	NOT_CLEARED,
	ABSENT;

	public boolean cleared() {
		return this == CLEARED;
	}
}
