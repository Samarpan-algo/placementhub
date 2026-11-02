package com.placementhub.application;

import java.util.List;

/**
 * The selected applications, plus every other application that a placement policy made INELIGIBLE
 * as a consequence, so the admin sees the full effect of the selection.
 */
public record SelectionResponse(
		Long driveId,
		List<Selected> selected,
		List<MadeIneligible> madeIneligible) {

	public record Selected(
			Long applicationId,
			Long studentId,
			String studentName,
			String rollNo,
			ApplicationStatus status) {
	}

	public record MadeIneligible(
			Long applicationId,
			Long studentId,
			String studentName,
			Long driveId,
			String companyName,
			String roleTitle,
			String reason) {
	}

}
