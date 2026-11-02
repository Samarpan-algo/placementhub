package com.placementhub.stats;

import static org.assertj.core.api.Assertions.assertThat;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

import com.placementhub.application.Application;
import com.placementhub.application.ApplicationStatus;
import com.placementhub.common.Branch;
import com.placementhub.company.Company;
import com.placementhub.drive.Drive;
import com.placementhub.drive.DriveStatus;
import com.placementhub.drive.JobType;
import com.placementhub.student.StudentProfile;
import com.placementhub.support.DbFixtures;
import com.placementhub.support.PostgresTest;
import com.placementhub.user.User;

import jakarta.persistence.EntityManager;

/**
 * The real aggregate SQL with exact numbers. The test first DELETES the drives, applications and student
 * profiles inside its own transaction, inserts a known data set, checks the stats, and is rolled back:
 * the dev data is back as it was afterwards (see PostgresTest).
 */
@PostgresTest
class StatsRepositoryDbTest {

	@Autowired
	private EntityManager em;
	@Autowired
	private DataSource dataSource;

	private StatsService service;
	private Company alpha;
	private Company beta;

	/*
	 * CSE: A (offers 10 and 20 -> best 20), B (offer 6), C (internship SELECTED only: not placed), D (nothing)
	 * IT:  E (offer 12), F (REJECTED), G (nothing)
	 * -> 7 students, 3 placed (42.86%); CSE 2 of 4 (50.00%), IT 1 of 3 (33.33%)
	 * -> best offers 20, 6, 12: highest 20.00, average 12.67, median 12.00
	 * -> offers: Alpha 2 (A, B), Beta 2 (A, E); the internship company has none
	 */
	@BeforeEach
	void setUp() {
		JdbcClient jdbc = JdbcClient.create(dataSource); // joins the test's transaction
		for (String table : new String[] { "round_results", "drive_rounds", "applications", "drive_branches",
				"drives", "student_profiles" }) {
			jdbc.sql("DELETE FROM " + table).update();
		}
		service = new StatsService(new StatsRepository(jdbc));

		DbFixtures db = new DbFixtures(em);
		User admin = db.admin();
		alpha = db.company("Alpha");
		beta = db.company("Beta");
		Company interns = db.company("Interns");
		Drive alpha10 = db.drive(alpha, admin, JobType.FULL_TIME, "10", DriveStatus.COMPLETED);
		Drive alpha6 = db.drive(alpha, admin, JobType.FULL_TIME, "6", DriveStatus.CLOSED);
		Drive beta20 = db.drive(beta, admin, JobType.FULL_TIME, "20", DriveStatus.COMPLETED);
		Drive beta12 = db.drive(beta, admin, JobType.FULL_TIME, "12", DriveStatus.COMPLETED);
		Drive internship = db.drive(interns, admin, JobType.INTERNSHIP, "3", DriveStatus.CLOSED);
		db.drive(alpha, admin, JobType.FULL_TIME, "8", DriveStatus.OPEN);

		StudentProfile a = db.student("A", Branch.CSE, "9.00");
		StudentProfile b = db.student("B", Branch.CSE, "8.00");
		StudentProfile c = db.student("C", Branch.CSE, "7.00");
		db.student("D", Branch.CSE, "6.50");
		StudentProfile e = db.student("E", Branch.IT, "8.50");
		StudentProfile f = db.student("F", Branch.IT, "7.50");
		db.student("G", Branch.IT, "7.00");

		selected(db, alpha10, a);
		selected(db, beta20, a);
		selected(db, alpha6, b);
		selected(db, internship, c);
		selected(db, beta12, e);
		db.application(beta12, f, ApplicationStatus.REJECTED);
		db.application(alpha6, c, ApplicationStatus.APPLIED);
		db.flushAndClear();
	}

	private static Application selected(DbFixtures db, Drive drive, StudentProfile student) {
		return db.application(drive, student, ApplicationStatus.SELECTED);
	}

	@Test
	void totalsAndPlacementPercentage() {
		StatsResponse stats = service.stats();

		assertThat(stats.totalStudents()).isEqualTo(7);
		assertThat(stats.placedStudents()).isEqualTo(3);
		assertThat(stats.placementPercentage()).isEqualByComparingTo("42.86");
	}

	@Test
	void branchWisePlacement() {
		StatsResponse stats = service.stats();

		assertThat(stats.branches()).containsExactly(
				new StatsResponse.BranchStats(Branch.CSE, 4, 2, new java.math.BigDecimal("50.00")),
				new StatsResponse.BranchStats(Branch.IT, 3, 1, new java.math.BigDecimal("33.33")));
	}

	@Test
	void ctcUsesEachPlacedStudentsBestOffer() {
		StatsResponse.CtcStats ctc = service.stats().ctc();

		assertThat(ctc.highestLpa()).isEqualByComparingTo("20.00");
		assertThat(ctc.averageLpa()).isEqualByComparingTo("12.67"); // (20 + 6 + 12) / 3, not over all 4 offers
		assertThat(ctc.medianLpa()).isEqualByComparingTo("12.00");
	}

	@Test
	void offersPerCompanyCountFullTimeSelectionsOnly() {
		StatsResponse stats = service.stats();

		assertThat(stats.offersByCompany()).containsExactly(
				new StatsResponse.CompanyOffers(alpha.getId(), alpha.getName(), 2),
				new StatsResponse.CompanyOffers(beta.getId(), beta.getName(), 2));
	}

	@Test
	void drivesByStatusIncludesZeros() {
		StatsResponse stats = service.stats();

		assertThat(stats.drivesByStatus()).containsEntry(DriveStatus.DRAFT, 0L)
				.containsEntry(DriveStatus.OPEN, 1L)
				.containsEntry(DriveStatus.CLOSED, 2L)
				.containsEntry(DriveStatus.COMPLETED, 3L);
	}

	@Test
	void nobodyPlacedGivesZeroPercentAndNullCtc() {
		JdbcClient.create(dataSource).sql("DELETE FROM applications").update();

		StatsResponse stats = service.stats();

		assertThat(stats.placedStudents()).isZero();
		assertThat(stats.placementPercentage()).isEqualByComparingTo("0.00");
		assertThat(stats.ctc()).isEqualTo(new StatsResponse.CtcStats(null, null, null));
		assertThat(stats.offersByCompany()).isEmpty();
	}

	@Test
	void evenNumberOfPlacedStudentsAveragesTheMiddleTwo() {
		// remove B's offer (6): best offers are now 20 and 12 -> median 16.00
		JdbcClient.create(dataSource).sql("""
				DELETE FROM applications WHERE status = 'SELECTED' AND drive_id IN
				    (SELECT id FROM drives WHERE ctc_lpa = 6)
				""").update();

		assertThat(service.stats().ctc().medianLpa()).isEqualByComparingTo("16.00");
	}

}
