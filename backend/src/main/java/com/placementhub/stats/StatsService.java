package com.placementhub.stats;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.placementhub.drive.DriveStatus;

/**
 * Admin statistics. The DB does the counting (StatsRepository: 4 aggregate queries); Java only adds up the
 * few branch rows, turns counts into percentages, and fills in drive statuses that have no drives.
 */
@Service
public class StatsService {

	private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

	private final StatsRepository statsRepository;

	public StatsService(StatsRepository statsRepository) {
		this.statsRepository = statsRepository;
	}

	/** readOnly: the 4 queries see one consistent snapshot of the data. */
	@Transactional(readOnly = true)
	public StatsResponse stats() {
		List<StatsRepository.BranchRow> branchRows = statsRepository.studentsByBranch();
		long totalStudents = branchRows.stream().mapToLong(StatsRepository.BranchRow::totalStudents).sum();
		long placedStudents = branchRows.stream().mapToLong(StatsRepository.BranchRow::placedStudents).sum();

		List<StatsResponse.BranchStats> branches = branchRows.stream()
				.map(r -> new StatsResponse.BranchStats(r.branch(), r.totalStudents(), r.placedStudents(),
						percentage(r.placedStudents(), r.totalStudents())))
				.toList();
		List<StatsResponse.CompanyOffers> offers = statsRepository.offersByCompany().stream()
				.map(r -> new StatsResponse.CompanyOffers(r.companyId(), r.companyName(), r.offers()))
				.toList();
		StatsRepository.CtcRow ctc = statsRepository.ctcOfBestOffers();

		return new StatsResponse(totalStudents, placedStudents, percentage(placedStudents, totalStudents), branches,
				offers, new StatsResponse.CtcStats(ctc.highestLpa(), ctc.averageLpa(), ctc.medianLpa()),
				drivesByStatus());
	}

	/** part / whole as a percentage with 2 decimals, half up; 0.00 when whole is 0 (no division by zero). */
	static BigDecimal percentage(long part, long whole) {
		if (whole == 0) {
			return BigDecimal.ZERO.setScale(2);
		}
		return BigDecimal.valueOf(part).multiply(HUNDRED).divide(BigDecimal.valueOf(whole), 2, RoundingMode.HALF_UP);
	}

	/** Every status in enum order, 0 for those without drives. */
	private Map<DriveStatus, Long> drivesByStatus() {
		Map<DriveStatus, Long> counts = new EnumMap<>(DriveStatus.class);
		for (DriveStatus status : DriveStatus.values()) {
			counts.put(status, 0L);
		}
		statsRepository.drivesByStatus().forEach(r -> counts.put(r.status(), r.drives()));
		return counts;
	}

}
