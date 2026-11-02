package com.placementhub.stats;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import com.placementhub.common.Branch;
import com.placementhub.drive.DriveStatus;

/**
 * Placement statistics for the TPO. Percentages have 2 decimals (0.00 when there are no students).
 * @param ctc over each placed student's best FULL_TIME offer; all null when nobody is placed yet
 * @param drivesByStatus every status, 0 included
 */
public record StatsResponse(
		long totalStudents,
		long placedStudents,
		BigDecimal placementPercentage,
		List<BranchStats> branches,
		List<CompanyOffers> offersByCompany,
		CtcStats ctc,
		Map<DriveStatus, Long> drivesByStatus) {

	public record BranchStats(Branch branch, long totalStudents, long placedStudents, BigDecimal placementPercentage) {
	}

	public record CompanyOffers(Long companyId, String companyName, long offers) {
	}

	public record CtcStats(BigDecimal highestLpa, BigDecimal averageLpa, BigDecimal medianLpa) {
	}

}
