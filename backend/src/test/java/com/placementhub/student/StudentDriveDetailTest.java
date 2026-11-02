package com.placementhub.student;

import static com.placementhub.policy.PolicyFixtures.fullTimeOffer;
import static com.placementhub.policy.PolicyFixtures.oneOffer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.placementhub.application.Application;
import com.placementhub.application.ApplicationRepository;
import com.placementhub.application.ApplicationStatus;
import com.placementhub.application.EligibilityService;
import com.placementhub.common.Branch;
import com.placementhub.common.ResourceNotFoundException;
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

/** GET /api/students/me/drives/{id}: OPEN drives, or drives the student applied to; everything else is 404. */
@ExtendWith(MockitoExtension.class)
class StudentDriveDetailTest {

	private static final Instant NOW = Instant.parse("2026-11-01T10:00:00Z");
	private static final Long STUDENT_ID = 9L;
	private static final Long DRIVE_ID = 7L;
	private static final String NOT_FOUND = "Drive with id 7 not found";

	@Mock
	private CurrentStudentResolver currentStudentResolver;
	@Mock
	private DriveRepository driveRepository;
	@Mock
	private ApplicationRepository applicationRepository;
	@Mock
	private PlacementPolicyRepository policyRepository;

	private StudentDriveService service;
	private StudentProfile student;
	private Drive drive;

	@BeforeEach
	void setUp() {
		service = new StudentDriveService(currentStudentResolver, driveRepository, new DriveMapper(),
				new EligibilityService(), applicationRepository, policyRepository, PolicyFixtures.engine(),
				Clock.fixed(NOW, ZoneOffset.UTC));

		// CSE 2027, CGPA 8.00, no backlogs: eligible for the drive below while it is OPEN
		student = new StudentProfile(new User("s@student.placementhub.dev", "hash", Role.STUDENT),
				"Test Student", "23CSE001", Branch.CSE, (short) 2027, new BigDecimal("8.00"), (short) 0);
		ReflectionTestUtils.setField(student, "id", STUDENT_ID);
		when(currentStudentResolver.requireProfile()).thenReturn(student);

		drive = new Drive(new Company("Nimbus Systems", null, null),
				new User("admin@placementhub.dev", "hash", Role.TPO_ADMIN));
		ReflectionTestUtils.setField(drive, "id", DRIVE_ID);
		drive.setRoleTitle("Backend Engineer");
		drive.setJobType(JobType.FULL_TIME);
		drive.setCtcLpa(new BigDecimal("10.00"));
		drive.setMinCgpa(new BigDecimal("7.00"));
		drive.setMaxBacklogs((short) 0);
		drive.setGraduationYear((short) 2027);
		drive.setRegistrationDeadline(NOW.plus(Duration.ofDays(5)));
		drive.setBranches(Set.of(Branch.CSE));
		drive.setStatus(DriveStatus.OPEN);
	}

	// ---------- visible ----------

	@Test
	void openDriveNotAppliedIsReturnedEligibleWithNoStatus() {
		givenDrive(DriveStatus.OPEN);
		givenNoApplication();

		StudentDriveResponse response = service.getOne(DRIVE_ID);

		assertThat(response.drive().id()).isEqualTo(DRIVE_ID);
		assertThat(response.drive().companyName()).isEqualTo("Nimbus Systems");
		assertThat(response.eligible()).isTrue();
		assertThat(response.reasons()).isEmpty();
		assertThat(response.applicationStatus()).isNull();
	}

	@Test
	void openDriveTheStudentFailsListsEveryReason() {
		drive.setMinCgpa(new BigDecimal("9.00"));
		drive.setBranches(Set.of(Branch.ECE));
		givenDrive(DriveStatus.OPEN);
		givenNoApplication();

		StudentDriveResponse response = service.getOne(DRIVE_ID);

		assertThat(response.eligible()).isFalse();
		assertThat(response.reasons()).containsExactly("Open only to ECE; your branch is CSE",
				"Minimum CGPA is 9.00; yours is 8.00");
	}

	@Test
	void openDriveAlreadyAppliedShowsTheApplicationStatus() {
		givenDrive(DriveStatus.OPEN);
		givenApplication(ApplicationStatus.APPLIED);

		assertThat(service.getOne(DRIVE_ID).applicationStatus()).isEqualTo(ApplicationStatus.APPLIED);
	}

	@ParameterizedTest
	@EnumSource(value = ApplicationStatus.class)
	void closedDriveIsVisibleWithAnyApplicationStatus(ApplicationStatus status) {
		givenDrive(DriveStatus.CLOSED);
		givenApplication(status);

		StudentDriveResponse response = service.getOne(DRIVE_ID);

		assertThat(response.applicationStatus()).isEqualTo(status);
		assertThat(response.drive().status()).isEqualTo(DriveStatus.CLOSED);
	}

	@Test
	void completedDriveTheStudentAppliedToIsVisibleButNeverEligible() {
		givenDrive(DriveStatus.COMPLETED);
		givenApplication(ApplicationStatus.SELECTED);

		StudentDriveResponse response = service.getOne(DRIVE_ID);

		assertThat(response.eligible()).isFalse();
		assertThat(response.reasons()).containsExactly("This drive is not open for applications (status: COMPLETED)");
	}

	@Test
	void policyReasonsAreAddedAfterEligibilityReasons() {
		givenDrive(DriveStatus.OPEN);
		givenNoApplication();
		when(policyRepository.findByActiveTrue()).thenReturn(List.of(oneOffer(true)));
		when(applicationRepository.findSelectedOffers(STUDENT_ID))
				.thenReturn(List.of(fullTimeOffer("Kaveri Analytics", "6.50")));

		StudentDriveResponse response = service.getOne(DRIVE_ID);

		assertThat(response.eligible()).isFalse();
		assertThat(response.reasons()).containsExactly(
				"One-offer policy: you already hold an offer of 6.50 LPA from Kaveri Analytics; "
						+ "students with an offer cannot apply to more full-time drives");
	}

	// ---------- hidden: the same 404 as a missing drive ----------

	@Test
	void missingDriveIs404AndSkipsTheApplicationQuery() {
		when(driveRepository.findById(DRIVE_ID)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.getOne(DRIVE_ID))
				.isInstanceOf(ResourceNotFoundException.class)
				.hasMessage(NOT_FOUND);
		verify(applicationRepository, never()).findByDriveIdAndStudentId(anyLong(), anyLong());
	}

	@Test
	void draftDriveIs404() {
		givenDrive(DriveStatus.DRAFT);
		givenNoApplication();

		assertThatThrownBy(() -> service.getOne(DRIVE_ID))
				.isInstanceOf(ResourceNotFoundException.class)
				.hasMessage(NOT_FOUND);
	}

	@ParameterizedTest
	@EnumSource(value = DriveStatus.class, names = { "CLOSED", "COMPLETED" })
	void closedDriveTheStudentNeverAppliedToIs404(DriveStatus status) {
		givenDrive(status);
		givenNoApplication();

		assertThatThrownBy(() -> service.getOne(DRIVE_ID))
				.isInstanceOf(ResourceNotFoundException.class)
				.hasMessage(NOT_FOUND);
		verify(policyRepository, never()).findByActiveTrue();
		verify(applicationRepository, never()).findSelectedOffers(any());
	}

	private void givenDrive(DriveStatus status) {
		drive.setStatus(status);
		when(driveRepository.findById(DRIVE_ID)).thenReturn(Optional.of(drive));
	}

	private void givenNoApplication() {
		when(applicationRepository.findByDriveIdAndStudentId(DRIVE_ID, STUDENT_ID)).thenReturn(Optional.empty());
	}

	private void givenApplication(ApplicationStatus status) {
		Application application = new Application(drive, student);
		application.setStatus(status);
		when(applicationRepository.findByDriveIdAndStudentId(DRIVE_ID, STUDENT_ID))
				.thenReturn(Optional.of(application));
	}

}
