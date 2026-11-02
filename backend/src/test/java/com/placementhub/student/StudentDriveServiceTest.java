package com.placementhub.student;

import static com.placementhub.policy.PolicyFixtures.dreamOffer;
import static com.placementhub.policy.PolicyFixtures.fullTimeOffer;
import static com.placementhub.policy.PolicyFixtures.oneOffer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import com.placementhub.application.ApplicationRepository;
import com.placementhub.application.ApplicationStatus;
import com.placementhub.application.DriveApplicationStatus;
import com.placementhub.application.EligibilityService;
import com.placementhub.common.Branch;
import com.placementhub.common.PageResponse;
import com.placementhub.company.Company;
import com.placementhub.drive.Drive;
import com.placementhub.drive.DriveMapper;
import com.placementhub.drive.DriveRepository;
import com.placementhub.drive.DriveStatus;
import com.placementhub.drive.JobType;
import com.placementhub.policy.PlacementPolicyRepository;
import com.placementhub.policy.PolicyFixtures;
import com.placementhub.user.Role;
import com.placementhub.user.User;

@ExtendWith(MockitoExtension.class)
class StudentDriveServiceTest {

	private static final Instant NOW = Instant.parse("2026-11-01T10:00:00Z");
	private static final Long STUDENT_ID = 9L;

	@Mock
	private CurrentStudentResolver currentStudentResolver;
	@Mock
	private DriveRepository driveRepository;
	@Mock
	private ApplicationRepository applicationRepository;
	@Mock
	private PlacementPolicyRepository policyRepository;

	private StudentDriveService service;
	private List<Drive> drives;
	private long nextDriveId = 1;
	private final User admin = new User("admin@placementhub.dev", "hash", Role.TPO_ADMIN);
	private final Company company = new Company("Nimbus Systems", null, null);

	@BeforeEach
	void setUp() {
		service = new StudentDriveService(currentStudentResolver, driveRepository, new DriveMapper(),
				new EligibilityService(), applicationRepository, policyRepository, PolicyFixtures.engine(),
				Clock.fixed(NOW, ZoneOffset.UTC));

		// CSE student, CGPA 8.00 -> eligible for drives with min CGPA <= 8.00
		StudentProfile student = new StudentProfile(new User("s@student.placementhub.dev", "hash", Role.STUDENT),
				"Test Student", "23CSE001", Branch.CSE, (short) 2027, new BigDecimal("8.00"), (short) 0);
		ReflectionTestUtils.setField(student, "id", STUDENT_ID);
		when(currentStudentResolver.requireProfile()).thenReturn(student);
		drives = List.of(
				drive("A eligible", "7.00"),     // id 1
				drive("B not eligible", "9.00"), // id 2
				drive("C eligible", "6.00"),     // id 3
				drive("D eligible", "8.00"));    // id 4
		when(driveRepository.findByStatusOrderByRegistrationDeadlineAsc(DriveStatus.OPEN)).thenReturn(drives);
		// The student applied to A and withdrew from C
		when(applicationRepository.findStatuses(STUDENT_ID, List.of(1L, 2L, 3L, 4L))).thenReturn(List.of(
				new DriveApplicationStatus(1L, 101L, ApplicationStatus.APPLIED),
				new DriveApplicationStatus(3L, 103L, ApplicationStatus.WITHDRAWN)));
	}

	@Test
	void applicationStatusIsShownForAppliedDrivesAndNullOtherwise() {
		PageResponse<StudentDriveResponse> page = service.browse(false, PageRequest.of(0, 20));

		assertThat(page.content()).extracting(StudentDriveResponse::applicationStatus)
				.containsExactly(ApplicationStatus.APPLIED, null, ApplicationStatus.WITHDRAWN, null);
		assertThat(page.content()).extracting(StudentDriveResponse::applicationId)
				.containsExactly(101L, null, 103L, null);
	}

	@Test
	void statusesAreLoadedWithOneQueryForAllDrives() {
		service.browse(false, PageRequest.of(0, 20));

		verify(applicationRepository, times(1)).findStatuses(STUDENT_ID, List.of(1L, 2L, 3L, 4L));
	}

	@Test
	void allOpenDrivesAreListedWithTheirEligibility() {
		PageResponse<StudentDriveResponse> page = service.browse(false, PageRequest.of(0, 20));

		assertThat(page.totalElements()).isEqualTo(4);
		assertThat(page.content()).extracting(StudentDriveResponse::eligible).containsExactly(true, false, true, true);
		assertThat(page.content().get(1).reasons()).containsExactly("Minimum CGPA is 9.00; yours is 8.00");
		assertThat(page.content().get(0).reasons()).isEmpty();
	}

	@Test
	void eligibleOnlyFiltersBeforePaging() {
		// page 1 with size 2 over the ELIGIBLE drives (A, C, D) must be [D], not a slice of all drives
		PageResponse<StudentDriveResponse> page = service.browse(true, PageRequest.of(1, 2));

		assertThat(page.totalElements()).isEqualTo(3);
		assertThat(page.totalPages()).isEqualTo(2);
		assertThat(page.content()).extracting(item -> item.drive().roleTitle()).containsExactly("D eligible");
		assertThat(page.content()).allMatch(StudentDriveResponse::eligible);
	}

	@Test
	void pageBeyondTheEndIsEmptyNotAnError() {
		PageResponse<StudentDriveResponse> page = service.browse(true, PageRequest.of(5, 2));

		assertThat(page.content()).isEmpty();
		assertThat(page.totalElements()).isEqualTo(3);
	}

	// ---------- placement policies ----------

	@Test
	void policyReasonsAreAddedAfterEligibilityReasonsAndMakeDrivesNotEligible() {
		when(policyRepository.findByActiveTrue()).thenReturn(List.of(oneOffer(true)));
		when(applicationRepository.findSelectedOffers(STUDENT_ID))
				.thenReturn(List.of(fullTimeOffer("Kaveri Analytics", "6.50")));
		String oneOfferReason = "One-offer policy: you already hold an offer of 6.50 LPA from Kaveri Analytics; "
				+ "students with an offer cannot apply to more full-time drives";

		PageResponse<StudentDriveResponse> page = service.browse(false, PageRequest.of(0, 20));

		assertThat(page.content()).extracting(StudentDriveResponse::eligible).containsOnly(false);
		assertThat(page.content().get(0).reasons()).containsExactly(oneOfferReason);
		assertThat(page.content().get(1).reasons()).containsExactly("Minimum CGPA is 9.00; yours is 8.00",
				oneOfferReason);
	}

	@Test
	void eligibleOnlyAlsoHidesPolicyBlockedDrives() {
		// one 6.00 offer, dream threshold 10: drives at 10.00 are upgrades, D at 8.00 is not
		drives.get(3).setCtcLpa(new BigDecimal("8.00"));
		when(policyRepository.findByActiveTrue()).thenReturn(List.of(dreamOffer(true, "10.00")));
		when(applicationRepository.findSelectedOffers(STUDENT_ID))
				.thenReturn(List.of(fullTimeOffer("Kaveri Analytics", "6.00")));

		PageResponse<StudentDriveResponse> page = service.browse(true, PageRequest.of(0, 20));

		assertThat(page.content()).extracting(item -> item.drive().roleTitle())
				.containsExactly("A eligible", "C eligible");
		assertThat(page.totalElements()).isEqualTo(2);
	}

	@Test
	void internshipDrivesAreNotBlockedByPolicies() {
		drives.get(2).setJobType(JobType.INTERNSHIP);
		when(policyRepository.findByActiveTrue()).thenReturn(List.of(oneOffer(true)));
		when(applicationRepository.findSelectedOffers(STUDENT_ID))
				.thenReturn(List.of(fullTimeOffer("Kaveri Analytics", "6.50")));

		PageResponse<StudentDriveResponse> page = service.browse(false, PageRequest.of(0, 20));

		assertThat(page.content()).extracting(StudentDriveResponse::eligible).containsExactly(false, false, true,
				false);
	}

	@Test
	void policiesAndOffersAreLoadedOnceForAllDrives() {
		when(policyRepository.findByActiveTrue()).thenReturn(List.of(dreamOffer(true, "10.00")));
		when(applicationRepository.findSelectedOffers(STUDENT_ID)).thenReturn(List.of());

		service.browse(false, PageRequest.of(0, 20));

		verify(policyRepository, times(1)).findByActiveTrue();
		verify(applicationRepository, times(1)).findSelectedOffers(STUDENT_ID);
	}

	@Test
	void noActivePolicySkipsTheOfferQuery() {
		when(policyRepository.findByActiveTrue()).thenReturn(List.of());

		service.browse(false, PageRequest.of(0, 20));

		verify(applicationRepository, never()).findSelectedOffers(any());
	}

	private Drive drive(String roleTitle, String minCgpa) {
		Drive drive = new Drive(company, admin);
		ReflectionTestUtils.setField(drive, "id", nextDriveId++);
		drive.setRoleTitle(roleTitle);
		drive.setJobType(JobType.FULL_TIME);
		drive.setCtcLpa(new BigDecimal("10.00"));
		drive.setMinCgpa(new BigDecimal(minCgpa));
		drive.setMaxBacklogs((short) 0);
		drive.setGraduationYear((short) 2027);
		drive.setRegistrationDeadline(NOW.plus(Duration.ofDays(5)));
		drive.setBranches(Set.of(Branch.CSE));
		drive.setStatus(DriveStatus.OPEN);
		return drive;
	}

}
