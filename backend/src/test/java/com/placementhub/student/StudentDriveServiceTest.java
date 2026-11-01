package com.placementhub.student;

import static org.assertj.core.api.Assertions.assertThat;
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

import com.placementhub.application.EligibilityService;
import com.placementhub.common.Branch;
import com.placementhub.common.PageResponse;
import com.placementhub.company.Company;
import com.placementhub.drive.Drive;
import com.placementhub.drive.DriveMapper;
import com.placementhub.drive.DriveRepository;
import com.placementhub.drive.DriveStatus;
import com.placementhub.drive.JobType;
import com.placementhub.user.Role;
import com.placementhub.user.User;

@ExtendWith(MockitoExtension.class)
class StudentDriveServiceTest {

	private static final Instant NOW = Instant.parse("2026-11-01T10:00:00Z");

	@Mock
	private CurrentStudentResolver currentStudentResolver;
	@Mock
	private DriveRepository driveRepository;

	private StudentDriveService service;
	private final User admin = new User("admin@placementhub.dev", "hash", Role.TPO_ADMIN);
	private final Company company = new Company("Nimbus Systems", null, null);

	@BeforeEach
	void setUp() {
		service = new StudentDriveService(currentStudentResolver, driveRepository, new DriveMapper(),
				new EligibilityService(), Clock.fixed(NOW, ZoneOffset.UTC));

		// CSE student, CGPA 8.00 -> eligible for drives with min CGPA <= 8.00
		StudentProfile student = new StudentProfile(new User("s@student.placementhub.dev", "hash", Role.STUDENT),
				"Test Student", "23CSE001", Branch.CSE, (short) 2027, new BigDecimal("8.00"), (short) 0);
		when(currentStudentResolver.requireProfile()).thenReturn(student);
		when(driveRepository.findByStatusOrderByRegistrationDeadlineAsc(DriveStatus.OPEN)).thenReturn(List.of(
				drive("A eligible", "7.00"),
				drive("B not eligible", "9.00"),
				drive("C eligible", "6.00"),
				drive("D eligible", "8.00")));
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

	private Drive drive(String roleTitle, String minCgpa) {
		Drive drive = new Drive(company, admin);
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
