package com.placementhub.application;

import static com.placementhub.round.RoundFixtures.application;
import static com.placementhub.round.RoundFixtures.drive;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.placementhub.audit.AuditAction;
import com.placementhub.audit.AuditEntityType;
import com.placementhub.audit.AuditService;
import com.placementhub.common.Branch;
import com.placementhub.drive.Drive;
import com.placementhub.drive.DriveStatus;
import com.placementhub.drive.JobType;
import com.placementhub.student.StudentProfile;
import com.placementhub.student.StudentProfileRepository;

/** Uses the real EligibilityService and StatusChanges: only the repositories and the audit writer are mocks. */
@ExtendWith(MockitoExtension.class)
class CriteriaChangeServiceTest {

	/** Before the fixtures' registration deadline (2026-10-25), so only the criteria decide. */
	private static final Instant NOW = Instant.parse("2026-10-20T10:00:00Z");
	private static final Long DRIVE_ID = 7L;

	@Mock
	private ApplicationRepository applicationRepository;
	@Mock
	private StudentProfileRepository studentProfileRepository;
	@Mock
	private AuditService auditService;

	private CriteriaChangeService service;
	private Drive drive;

	@BeforeEach
	void setUp() {
		service = new CriteriaChangeService(applicationRepository, studentProfileRepository,
				new EligibilityService(), new StatusChanges(auditService), Clock.fixed(NOW, ZoneOffset.UTC));
		// The NEW criteria, already applied by DriveService: CSE only, CGPA 8.00+, no backlogs, 2027 batch
		drive = drive(DRIVE_ID, "Nimbus Systems", JobType.FULL_TIME, "12", DriveStatus.OPEN);
		drive.setBranches(Set.of(Branch.CSE));
		drive.setMinCgpa(new BigDecimal("8.00"));
		drive.setMaxBacklogs((short) 0);
		drive.setGraduationYear((short) 2027);
	}

	private void pending(Application... applications) {
		List<Long> studentIds = List.of(applications).stream().map(a -> a.getStudent().getId()).toList();
		when(applicationRepository.findStudentIdsByDriveIdAndStatusIn(DRIVE_ID, CriteriaChangeService.RECHECKED))
				.thenReturn(studentIds);
		when(applicationRepository.findWithStudentByDriveIdAndStatusIn(DRIVE_ID, CriteriaChangeService.RECHECKED))
				.thenReturn(List.of(applications));
	}

	private static StudentProfile student(Long id, String name, String cgpa) {
		StudentProfile student = com.placementhub.round.RoundFixtures.student(id, name); // CSE, 2027, 0 backlogs
		ReflectionTestUtils.setField(student, "cgpa", new BigDecimal(cgpa));
		return student;
	}

	@Test
	void anApplicationThatNowFailsBecomesIneligibleWithTheReason() {
		Application riya = application(11L, drive, student(1L, "Riya", "7.50"), ApplicationStatus.APPLIED);
		pending(riya);

		int count = service.reevaluate(drive);

		assertThat(count).isEqualTo(1);
		assertThat(riya.getStatus()).isEqualTo(ApplicationStatus.INELIGIBLE);
		assertThat(riya.getStatusReason()).isEqualTo("Drive criteria changed: Minimum CGPA is 8.00; yours is 7.50");
	}

	@Test
	void anApplicationThatStillPassesIsLeftAlone() {
		Application karan = application(12L, drive, student(2L, "Karan", "8.50"), ApplicationStatus.SHORTLISTED);
		pending(karan);

		int count = service.reevaluate(drive);

		assertThat(count).isZero();
		assertThat(karan.getStatus()).isEqualTo(ApplicationStatus.SHORTLISTED);
		verify(auditService, never()).record(any(), anyLong(), any(), any(), any());
	}

	@Test
	void everyFailingReasonIsJoinedIntoOneReason() {
		StudentProfile meera = student(3L, "Meera", "6.00");
		ReflectionTestUtils.setField(meera, "branch", Branch.IT);
		Application application = application(13L, drive, meera, ApplicationStatus.APPLIED);
		pending(application);

		service.reevaluate(drive);

		assertThat(application.getStatusReason()).isEqualTo("Drive criteria changed: Open only to CSE; your branch "
				+ "is IT; Minimum CGPA is 8.00; yours is 6.00");
	}

	@Test
	void countsOnlyTheApplicationsMadeIneligible() {
		Application riya = application(11L, drive, student(1L, "Riya", "7.50"), ApplicationStatus.APPLIED);
		Application karan = application(12L, drive, student(2L, "Karan", "9.00"), ApplicationStatus.APPLIED);
		Application meera = application(13L, drive, student(3L, "Meera", "7.90"), ApplicationStatus.SHORTLISTED);
		pending(riya, karan, meera);

		assertThat(service.reevaluate(drive)).isEqualTo(2);
		assertThat(karan.getStatus()).isEqualTo(ApplicationStatus.APPLIED);
		verify(auditService, times(2)).record(eq(AuditEntityType.APPLICATION), anyLong(),
				eq(AuditAction.STATUS_CHANGED), any(), any());
	}

	@Test
	@SuppressWarnings({ "unchecked", "rawtypes" })
	void theChangeIsAuditedWithTheCriteriaChangeTrigger() {
		Application riya = application(11L, drive, student(1L, "Riya", "7.50"), ApplicationStatus.APPLIED);
		pending(riya);

		service.reevaluate(drive);

		ArgumentCaptor<Map<String, Object>> oldValue = (ArgumentCaptor) ArgumentCaptor.forClass(Map.class);
		ArgumentCaptor<Map<String, Object>> newValue = (ArgumentCaptor) ArgumentCaptor.forClass(Map.class);
		verify(auditService).record(eq(AuditEntityType.APPLICATION), eq(11L), eq(AuditAction.STATUS_CHANGED),
				oldValue.capture(), newValue.capture());
		assertThat(oldValue.getValue()).containsEntry("status", "APPLIED");
		assertThat(newValue.getValue()).containsEntry("status", "INELIGIBLE")
				.containsEntry("trigger", "CRITERIA_CHANGE")
				.containsEntry("reason", "Drive criteria changed: Minimum CGPA is 8.00; yours is 7.50");
	}

	@Test
	void onlyAppliedAndShortlistedAreReChecked() {
		assertThat(CriteriaChangeService.RECHECKED)
				.isEqualTo(EnumSet.of(ApplicationStatus.APPLIED, ApplicationStatus.SHORTLISTED));
	}

	@Test
	void locksTheStudentsBeforeLoadingTheApplications() {
		pending(application(11L, drive, student(1L, "Riya", "9.00"), ApplicationStatus.APPLIED));

		service.reevaluate(drive);

		InOrder order = inOrder(applicationRepository, studentProfileRepository);
		order.verify(applicationRepository).findStudentIdsByDriveIdAndStatusIn(DRIVE_ID,
				CriteriaChangeService.RECHECKED);
		order.verify(studentProfileRepository).findAllByIdForUpdate(List.of(1L));
		order.verify(applicationRepository).findWithStudentByDriveIdAndStatusIn(DRIVE_ID,
				CriteriaChangeService.RECHECKED);
	}

	@Test
	void noPendingApplicationsMeansNoLocksAndNoLoad() {
		when(applicationRepository.findStudentIdsByDriveIdAndStatusIn(DRIVE_ID, CriteriaChangeService.RECHECKED))
				.thenReturn(List.of());

		assertThat(service.reevaluate(drive)).isZero();
		verify(studentProfileRepository, never()).findAllByIdForUpdate(anyCollection());
		verify(applicationRepository, never()).findWithStudentByDriveIdAndStatusIn(any(), any());
	}

}
