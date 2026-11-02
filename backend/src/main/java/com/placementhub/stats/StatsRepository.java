package com.placementhub.stats;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import com.placementhub.common.Branch;
import com.placementhub.drive.DriveStatus;

/**
 * Placement statistics as aggregate SQL: PostgreSQL counts, groups and averages, and only the small result
 * rows (one per branch, company or status) come back to Java. Native SQL through JdbcClient because
 * JPQL has no median (percentile_cont) and these are read-only reports, not entities.
 *
 * An OFFER is an application SELECTED in a FULL_TIME drive (decision 40); a PLACED student has at least one.
 */
@Repository
public class StatsRepository {

	/** The one definition of an offer, shared by every query below. */
	private static final String OFFERS = """
			SELECT a.student_id, d.company_id, d.ctc_lpa
			FROM applications a JOIN drives d ON d.id = a.drive_id
			WHERE a.status = 'SELECTED' AND d.job_type = 'FULL_TIME'
			""";

	private final JdbcClient jdbcClient;

	public StatsRepository(JdbcClient jdbcClient) {
		this.jdbcClient = jdbcClient;
	}

	record BranchRow(Branch branch, long totalStudents, long placedStudents) {
	}

	record CompanyRow(Long companyId, String companyName, long offers) {
	}

	/** null values when nobody is placed yet. */
	record CtcRow(BigDecimal highestLpa, BigDecimal averageLpa, BigDecimal medianLpa) {
	}

	record StatusRow(DriveStatus status, long drives) {
	}

	/** Every branch that has students: how many, and how many are placed. */
	List<BranchRow> studentsByBranch() {
		return jdbcClient.sql("""
				WITH offers AS (%s)
				SELECT s.branch AS branch,
				       COUNT(*) AS total_students,
				       COUNT(*) FILTER (WHERE EXISTS (SELECT 1 FROM offers o WHERE o.student_id = s.id))
				           AS placed_students
				FROM student_profiles s
				GROUP BY s.branch
				ORDER BY s.branch
				""".formatted(OFFERS))
				.query(BranchRow.class)
				.list();
	}

	/** Companies with at least one offer, most offers first. */
	List<CompanyRow> offersByCompany() {
		return jdbcClient.sql("""
				WITH offers AS (%s)
				SELECT c.id AS company_id, c.name AS company_name, COUNT(*) AS offers
				FROM offers o JOIN companies c ON c.id = o.company_id
				GROUP BY c.id, c.name
				ORDER BY offers DESC, c.name
				""".formatted(OFFERS))
				.query(CompanyRow.class)
				.list();
	}

	/**
	 * Highest, average and median CTC over each placed student's BEST offer (how colleges report "average
	 * package"): a student with two offers counts once, with the higher one.
	 */
	CtcRow ctcOfBestOffers() {
		return jdbcClient.sql("""
				WITH offers AS (%s),
				     best AS (SELECT student_id, MAX(ctc_lpa) AS ctc FROM offers GROUP BY student_id)
				SELECT MAX(ctc) AS highest_lpa,
				       ROUND(AVG(ctc), 2) AS average_lpa,
				       ROUND(CAST(percentile_cont(0.5) WITHIN GROUP (ORDER BY ctc) AS NUMERIC), 2) AS median_lpa
				FROM best
				""".formatted(OFFERS))
				.query(CtcRow.class)
				.single();
	}

	/** Only statuses that have drives; the service fills in the zeros. */
	List<StatusRow> drivesByStatus() {
		return jdbcClient.sql("SELECT status, COUNT(*) AS drives FROM drives GROUP BY status")
				.query(StatusRow.class)
				.list();
	}

}
