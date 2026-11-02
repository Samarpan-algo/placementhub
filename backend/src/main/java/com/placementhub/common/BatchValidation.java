package com.placementhub.common;

import java.util.ArrayList;
import java.util.List;

/**
 * Collects every problem of a batch request before anything is saved (all-or-nothing).
 * Problems keep the order of the items. If there are any, one exception lists them all:
 * 400 if at least one item is a bad request (e.g. unknown id), otherwise 409 (state conflicts).
 */
public class BatchValidation {

	private final List<String> problems = new ArrayList<>();
	private boolean anyBadRequest;

	/** The item itself is wrong for this request (duplicate, not part of this drive). */
	public void badRequest(String problem) {
		problems.add(problem);
		anyBadRequest = true;
	}

	/** The item is valid, but its current state doesn't allow the change. */
	public void conflict(String problem) {
		problems.add(problem);
	}

	public boolean hasProblems() {
		return !problems.isEmpty();
	}

	/** @param what e.g. "Round results were not saved" */
	public void throwIfAny(String what) {
		if (problems.isEmpty()) {
			return;
		}
		String message = what + ": " + problems.size() + (problems.size() == 1 ? " problem" : " problems")
				+ " found, see details. Nothing was changed";
		if (anyBadRequest) {
			throw new BadRequestException(message, problems);
		}
		throw new ConflictException(message, problems);
	}

}
