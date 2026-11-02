package com.placementhub.policy;

/** One rule's verdict: allowed, or blocked with a reason the student can read. */
public record PolicyResult(boolean allowed, String reason) {

	private static final PolicyResult ALLOWED = new PolicyResult(true, null);

	public static PolicyResult allow() {
		return ALLOWED;
	}

	public static PolicyResult block(String reason) {
		return new PolicyResult(false, reason);
	}

}
