package com.placementhub.application;

import java.util.List;

/** Outcome of an eligibility check: eligible only if there are no failing reasons. */
public record EligibilityResult(boolean eligible, List<String> reasons) {

	public static EligibilityResult of(List<String> reasons) {
		return new EligibilityResult(reasons.isEmpty(), List.copyOf(reasons));
	}

}
