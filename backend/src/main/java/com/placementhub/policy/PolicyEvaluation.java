package com.placementhub.policy;

import java.util.List;

/** Outcome of all active policies together: allowed only if no rule blocked. */
public record PolicyEvaluation(boolean allowed, List<String> reasons) {

	public static final PolicyEvaluation ALLOWED = new PolicyEvaluation(true, List.of());

	public static PolicyEvaluation of(List<String> reasons) {
		return new PolicyEvaluation(reasons.isEmpty(), List.copyOf(reasons));
	}

}
