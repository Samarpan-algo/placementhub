package com.placementhub.policy;

import java.math.BigDecimal;

import com.placementhub.drive.JobType;

/**
 * One SELECTED application of a student, with just what policy messages need.
 * Only FULL_TIME ones count as offers; PolicyEngine filters out the rest.
 */
public record Offer(String companyName, BigDecimal ctcLpa, JobType jobType) {

	public boolean isFullTime() {
		return jobType == JobType.FULL_TIME;
	}

}
