package com.placementhub.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import com.placementhub.common.Branch;
import com.placementhub.common.PageResponse;
import com.placementhub.drive.Drive;
import com.placementhub.drive.DriveStatus;
import com.placementhub.drive.JobType;
import com.placementhub.round.DriveRound;
import com.placementhub.round.RoundOutcome;
import com.placementhub.student.StudentProfile;
import com.placementhub.support.DbFixtures;
import com.placementhub.support.PostgresTest;
import com.placementhub.user.User;

import jakarta.persistence.EntityManager;

/** The real applicants query: filters with null parameters, round progress, and the number of SQL statements. */
@PostgresTest
@Import(ApplicantService.class)
class ApplicantServiceDbTest {

	private static final ApplicantFilter NO_FILTER = new ApplicantFilter(null, null, null);
	private static final PageRequest FIRST_PAGE = PageRequest.of(0, 20, Sort.by("student.rollNo"));

	@Autowired
	private EntityManager em;
	@Autowired
	private ApplicantService service;

	private DbFixtures db;
	private Drive drive;
	private Application riya;
	private Application karan;
	private Application meera;

	@BeforeEach
	void setUp() {
		db = new DbFixtures(em);
		User admin = db.admin();
		drive = db.drive(db.company("Nimbus"), admin, JobType.FULL_TIME, "12", DriveStatus.CLOSED);
		DriveRound aptitude = db.round(drive, 1, "Aptitude");
		DriveRound technical = db.round(drive, 2, "Technical");
		db.round(drive, 3, "HR");
		StudentProfile s1 = db.student("Riya", Branch.CSE, "8.50");
		StudentProfile s2 = db.student("Karan", Branch.IT, "7.00");
		StudentProfile s3 = db.student("Meera", Branch.CSE, "6.20");
		riya = db.application(drive, s1, ApplicationStatus.REJECTED);
		karan = db.application(drive, s2, ApplicationStatus.SHORTLISTED);
		meera = db.application(drive, s3, ApplicationStatus.APPLIED);
		db.result(aptitude, riya, RoundOutcome.CLEARED);
		db.result(technical, riya, RoundOutcome.NOT_CLEARED);
		db.result(aptitude, karan, RoundOutcome.CLEARED);
		db.flushAndClear();
	}

	@Test
	void everyApplicantWithStudentDetailsAndTheirLatestRound() {
		PageResponse<ApplicantResponse> page = service.list(drive.getId(), NO_FILTER, FIRST_PAGE);

		assertThat(page.totalElements()).isEqualTo(3);
		ApplicantResponse r = row(page, riya);
		assertThat(r.fullName()).isEqualTo("Riya");
		assertThat(r.email()).endsWith("@test.dev");
		assertThat(r.branch()).isEqualTo(Branch.CSE);
		assertThat(r.cgpa()).isEqualByComparingTo("8.50");
		assertThat(r.progress().totalRounds()).isEqualTo(3);
		assertThat(r.progress().latestRound().roundNo()).isEqualTo(2);
		assertThat(r.progress().latestRound().name()).isEqualTo("Technical");
		assertThat(r.progress().latestRound().result()).isEqualTo(RoundOutcome.NOT_CLEARED);
		assertThat(row(page, karan).progress().latestRound().roundNo()).isEqualTo(1);
		assertThat(row(page, meera).progress().latestRound()).isNull();
	}

	@Test
	void filtersByStatus() {
		assertThat(ids(service.list(drive.getId(), new ApplicantFilter(ApplicationStatus.SHORTLISTED, null, null),
				FIRST_PAGE))).containsExactly(karan.getId());
	}

	@Test
	void filtersByBranch() {
		assertThat(ids(service.list(drive.getId(), new ApplicantFilter(null, Branch.CSE, null), FIRST_PAGE)))
				.containsExactlyInAnyOrder(riya.getId(), meera.getId());
	}

	@Test
	void minCgpaIsInclusive() {
		assertThat(ids(service.list(drive.getId(), new ApplicantFilter(null, null, new BigDecimal("7")), FIRST_PAGE)))
				.containsExactlyInAnyOrder(riya.getId(), karan.getId());
	}

	@Test
	void filtersCombine() {
		assertThat(ids(service.list(drive.getId(),
				new ApplicantFilter(ApplicationStatus.APPLIED, Branch.CSE, new BigDecimal("6.00")), FIRST_PAGE)))
				.containsExactly(meera.getId());
	}

	@Test
	void pagesAndCountsAcrossPages() {
		PageResponse<ApplicantResponse> page = service.list(drive.getId(), NO_FILTER,
				PageRequest.of(1, 2, Sort.by("student.rollNo")));

		assertThat(page.content()).hasSize(1);
		assertThat(page.totalElements()).isEqualTo(3);
		assertThat(page.totalPages()).isEqualTo(2);
	}

	@Test
	void sameNumberOfStatementsForOneApplicantOrMany() {
		long withOne = statementsFor(() -> service.list(drive.getId(),
				new ApplicantFilter(ApplicationStatus.APPLIED, null, null), PageRequest.of(0, 1)));
		long withThree = statementsFor(() -> service.list(drive.getId(), NO_FILTER, PageRequest.of(0, 2)));
		long exportAll = statementsFor(() -> service.listAll(drive.getId(), NO_FILTER));

		// drive exists + applications (student and user JOINed) + count + round count + results IN (...)
		assertThat(withOne).isEqualTo(5);
		assertThat(withThree).isEqualTo(5);
		// unpaged: no count query
		assertThat(exportAll).isEqualTo(4);
	}

	@Test
	void exportIsSortedByRollNumber() {
		List<ApplicantResponse> all = service.listAll(drive.getId(), NO_FILTER);

		assertThat(all).extracting(ApplicantResponse::rollNo).isSorted();
		assertThat(all).hasSize(3);
	}

	private long statementsFor(Runnable call) {
		em.clear();
		Statistics statistics = db.statistics();
		statistics.clear();
		call.run();
		return statistics.getPrepareStatementCount();
	}

	private static ApplicantResponse row(PageResponse<ApplicantResponse> page, Application application) {
		return page.content().stream().filter(r -> r.applicationId().equals(application.getId())).findFirst()
				.orElseThrow();
	}

	private static List<Long> ids(PageResponse<ApplicantResponse> page) {
		return page.content().stream().map(ApplicantResponse::applicationId).toList();
	}

}
