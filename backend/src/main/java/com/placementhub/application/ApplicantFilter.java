package com.placementhub.application;

import java.math.BigDecimal;

import com.placementhub.common.Branch;

/** Optional filters for a drive's applicants. A null field means "no filter on this". */
public record ApplicantFilter(ApplicationStatus status, Branch branch, BigDecimal minCgpa) {
}
