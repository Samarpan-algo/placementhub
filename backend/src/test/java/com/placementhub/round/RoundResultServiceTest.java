package com.placementhub.round;

import static com.placementhub.round.RoundFixtures.application;
import static com.placementhub.round.RoundFixtures.drive;
import static com.placementhub.round.RoundFixtures.result;
import static com.placementhub.round.RoundFixtures.round;
import static com.placementhub.round.RoundFixtures.student;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyShort;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.placementhub.application.Application;
import com.placementhub.application.ApplicationRepository;
import com.placementhub.application.ApplicationStatus;
import com.placementhub.application.StatusChanges;
import com.placementhub.audit.AuditAction;
import com.placementhub.audit.AuditEntityType;
import com.placementhub.audit.AuditService;
import com.placementhub.common.BadRequestException;
import com.placementhub.common.ConflictException;
import com.placementhub.common.ResourceNotFoundException;
import com.placementhub.drive.Drive;
import com.placementhub.drive.DriveRepository;
import com.placementhub.drive.DriveStatus;
import com.placementhub.drive.JobType;
import com.placementhub.student.StudentProfile;
import com.placementhub.student.StudentProfileRepository;

@ExtendWith(MockitoExtension.class)
class RoundResultServiceTest {

	private static final Long DRIVE_ID = 7L;

	@Mock
	private DriveRepository driveRepository;
	@Mock
	private DriveRoundRepository roundRepository;
	@Mock
	private RoundResultRepository resultRepository;
	@Mock
	private ApplicationRepository applicationRepository;
	@Mock
	private StudentProfileRepository studentProfileRepository;
	@Mock
	private AuditService auditService;

	private RoundResultService service;
	private Drive drive;
	private DriveRound round1;
	private DriveRound round2;
	private Application riya;
	private Application karan;
	private Application meera;
	/** Results "already in the DB" before the request. */
	private final List<RoundResult> existing = new ArrayList<>();

	@BeforeEach
	void setUp() {
		service = new RoundResultService(driveRepository, roundRepository, resultRepository, applicationRepository,
				studentProfileRepository, new StatusChanges(auditService));
		drive = drive(DRIVE_ID, "Nimbus Systems", JobType.FULL_TIME, "12", DriveStatus.CLOSED);
		round1 = round(101L, drive, 1, "Aptitude");
		round2 = round(102L, drive, 2, "Technical");
		StudentProfile s1 = student(1L, "Riya");
		StudentProfile s2 = student(2L, "Karan");
		StudentProfile s3 = student(3L, "Meera");
		riya = application(11L, drive, s1, ApplicationStatus.APPLIED);
		karan = application(12L, drive, s2, ApplicationStatus.APPLIED);
		meera = application(13L, drive, s3, ApplicationStatus.APPLIED);

		lenient().when(driveRepository.findByIdForUpdate(DRIVE_ID)).thenReturn(Optional.of(drive));
		lenient().when(roundRepository.findByIdAndDriveId(101L, DRIVE_ID)).thenReturn(Optional.of(round1));
		lenient().when(roundRepository.findByIdAndDriveId(102L, DRIVE_ID)).thenReturn(Optional.of(round2));
		lenient().when(roundRepository.findByDriveIdAndRoundNo(DRIVE_ID, (short) 1)).thenReturn(Optional.of(round1));
		lenient().when(applicationRepository.findStudentIdsByIdInAndDriveId(anyCollection(), eq(DRIVE_ID)))
				.thenReturn(List.of(1L, 2L, 3L));
		// Only applications of this drive with the requested ids come back, as the real query does
		lenient().when(applicationRepository.findByIdInAndDriveId(anyCollection(), eq(DRIVE_ID)))
				.thenAnswer(inv -> {
					Collection<Long> ids = inv.getArgument(0);
					return List.of(riya, karan, meera).stream().filter(a -> ids.contains(a.getId())).toList();
				});
		lenient().when(resultRepository.findWithRoundByApplicationIdIn(anyCollection()))
				.thenAnswer(inv -> new ArrayList<>(existing));
	}

	private static RoundResultsRequest.Item item(Application application, RoundOutcome outcome) {
		return new RoundResultsRequest.Item(application.getId(), outcome, null);
	}

	private RoundResultsResponse save(DriveRound round, RoundResultsRequest.Item... items) {
		return service.saveResults(DRIVE_ID, round.getId(), new RoundResultsRequest(List.of(items)));
	}

	// =====================================================================
	// status effects
	// =====================================================================

	@Nested
	class StatusEffects {

		@Test
		void clearedShortlistsAndNotClearedOrAbsentRejectWithTheRoundName() {
			RoundResultsResponse response = save(round1, item(riya, RoundOutcome.CLEARED),
					item(karan, RoundOutcome.NOT_CLEARED), item(meera, RoundOutcome.ABSENT));

			assertThat(riya.getStatus()).isEqualTo(ApplicationStatus.SHORTLISTED);
			assertThat(riya.getStatusReason()).isNull();
			assertThat(karan.getStatus()).isEqualTo(ApplicationStatus.REJECTED);
			assertThat(karan.getStatusReason()).isEqualTo("Not cleared in Round 1: Aptitude");
			assertThat(meera.getStatus()).isEqualTo(ApplicationStatus.REJECTED);
			assertThat(meera.getStatusReason()).isEqualTo("Not cleared in Round 1: Aptitude");
			verify(resultRepository, times(3)).save(any(RoundResult.class));
			assertThat(response.results()).extracting(RoundResultsResponse.Item::applicationStatus)
					.containsExactly(ApplicationStatus.SHORTLISTED, ApplicationStatus.REJECTED,
							ApplicationStatus.REJECTED);
			assertThat(response.results().get(0).studentName()).isEqualTo("Riya");
		}

		@Test
		void everyStatusChangeIsAuditedWithTheRoundResultTrigger() {
			save(round1, item(riya, RoundOutcome.CLEARED), item(karan, RoundOutcome.NOT_CLEARED));

			Map<String, Object> riyaNew = auditedNewValue(11L);
			assertThat(riyaNew).containsEntry("status", "SHORTLISTED").containsEntry("trigger", "ROUND_RESULT");
			Map<String, Object> karanNew = auditedNewValue(12L);
			assertThat(karanNew).containsEntry("status", "REJECTED")
					.containsEntry("reason", "Not cleared in Round 1: Aptitude");
		}

		@Test
		void remarksAreTrimmedAndBlankBecomesNull() {
			RoundResultsResponse response = service.saveResults(DRIVE_ID, 101L, new RoundResultsRequest(List.of(
					new RoundResultsRequest.Item(11L, RoundOutcome.CLEARED, "  Strong quant  "),
					new RoundResultsRequest.Item(12L, RoundOutcome.CLEARED, "   "))));

			assertThat(response.results()).extracting(RoundResultsResponse.Item::remarks)
					.containsExactly("Strong quant", null);
		}

		@Test
		void locksTheDriveThenTheStudentsBeforeLoadingTheApplications() {
			save(round1, item(riya, RoundOutcome.CLEARED));

			InOrder order = inOrder(driveRepository, studentProfileRepository, applicationRepository);
			order.verify(driveRepository).findByIdForUpdate(DRIVE_ID);
			order.verify(studentProfileRepository).findAllByIdForUpdate(List.of(1L, 2L, 3L));
			order.verify(applicationRepository).findByIdInAndDriveId(anyCollection(), eq(DRIVE_ID));
		}

	}

	// =====================================================================
	// sequencing
	// =====================================================================

	@Nested
	class Sequencing {

		@Test
		void round2IsAllowedAfterClearingRound1() {
			riya.setStatus(ApplicationStatus.SHORTLISTED);
			existing.add(result(round1, riya, RoundOutcome.CLEARED));

			save(round2, item(riya, RoundOutcome.NOT_CLEARED));

			assertThat(riya.getStatus()).isEqualTo(ApplicationStatus.REJECTED);
			assertThat(riya.getStatusReason()).isEqualTo("Not cleared in Round 2: Technical");
		}

		@Test
		void round2WithoutARound1ResultIs409() {
			assertThatThrownBy(() -> save(round2, item(riya, RoundOutcome.CLEARED)))
					.isInstanceOfSatisfying(ConflictException.class, ex -> assertThat(ex.getDetails()).containsExactly(
							"Application 11 must have CLEARED Round 1: Aptitude before Round 2: Technical "
									+ "(current result: no result)"));
		}

		@Test
		void round2AfterNotClearingRound1Is409() {
			karan.setStatus(ApplicationStatus.REJECTED);
			existing.add(result(round1, karan, RoundOutcome.NOT_CLEARED));

			assertThatThrownBy(() -> save(round2, item(karan, RoundOutcome.CLEARED)))
					.isInstanceOfSatisfying(ConflictException.class, ex -> assertThat(ex.getDetails()).containsExactly(
							"Application 12 must have CLEARED Round 1: Aptitude before Round 2: Technical "
									+ "(current result: NOT_CLEARED)"));
		}

		@Test
		void round1NeedsNoPreviousRound() {
			save(round1, item(riya, RoundOutcome.CLEARED));

			verify(roundRepository, never()).findByDriveIdAndRoundNo(anyLong(), anyShort());
		}

	}

	// =====================================================================
	// all or nothing
	// =====================================================================

	@Nested
	class AllOrNothing {

		@Test
		void oneBadItemMeansNothingIsSavedAndEveryProblemIsListed() {
			karan.setStatus(ApplicationStatus.WITHDRAWN);
			meera.setStatus(ApplicationStatus.INELIGIBLE);
			meera.setStatusReason("One-offer policy: you already hold an offer");

			assertThatThrownBy(() -> save(round1, item(riya, RoundOutcome.CLEARED),
					item(karan, RoundOutcome.CLEARED), item(meera, RoundOutcome.CLEARED)))
					.isInstanceOfSatisfying(ConflictException.class, ex -> {
						assertThat(ex.getMessage()).isEqualTo(
								"Round results were not saved: 2 problems found, see details. Nothing was changed");
						assertThat(ex.getDetails()).containsExactly(
								"Application 12 is WITHDRAWN; round results can't be recorded for it",
								"Application 13 is INELIGIBLE (One-offer policy: you already hold an offer); "
										+ "round results can't be recorded for it");
					});

			// riya was valid, but nothing was saved for her either
			assertThat(riya.getStatus()).isEqualTo(ApplicationStatus.APPLIED);
			verify(resultRepository, never()).save(any());
			verify(auditService, never()).record(any(), anyLong(), any(), any(), any());
		}

		@Test
		void duplicateOrForeignApplicationsMake400AndConflictsAreListedToo() {
			karan.setStatus(ApplicationStatus.SELECTED);

			assertThatThrownBy(() -> service.saveResults(DRIVE_ID, 101L, new RoundResultsRequest(List.of(
					item(riya, RoundOutcome.CLEARED),
					item(riya, RoundOutcome.NOT_CLEARED),
					new RoundResultsRequest.Item(99L, RoundOutcome.CLEARED, null),
					item(karan, RoundOutcome.CLEARED)))))
					.isInstanceOfSatisfying(BadRequestException.class, ex -> assertThat(ex.getDetails())
							.containsExactly(
									"Application 11 is listed more than once in this batch",
									"Application 99 was not found in this drive",
									"Application 12 is SELECTED; round results can't be recorded for it"));
			verify(resultRepository, never()).save(any());
		}

		@ParameterizedTest
		@EnumSource(value = DriveStatus.class, names = { "OPEN", "COMPLETED" })
		void driveMustBeClosed(DriveStatus status) {
			drive.setStatus(status);

			assertThatThrownBy(() -> save(round1, item(riya, RoundOutcome.CLEARED)))
					.isInstanceOf(ConflictException.class)
					.hasMessage("Round results can be recorded only while the drive is CLOSED (status: " + status + ")");
			verify(studentProfileRepository, never()).findAllByIdForUpdate(any());
		}

		@Test
		void roundOfAnotherDriveIs404() {
			when(roundRepository.findByIdAndDriveId(999L, DRIVE_ID)).thenReturn(Optional.empty());

			assertThatThrownBy(() -> service.saveResults(DRIVE_ID, 999L,
					new RoundResultsRequest(List.of(item(riya, RoundOutcome.CLEARED)))))
					.isInstanceOf(ResourceNotFoundException.class)
					.hasMessage("Round with id 999 not found");
		}

	}

	// =====================================================================
	// corrections (upsert)
	// =====================================================================

	@Nested
	class Corrections {

		@Test
		void notClearedCorrectedToClearedUpdatesTheRowAndShortlistsAgain() {
			karan.setStatus(ApplicationStatus.REJECTED);
			karan.setStatusReason("Not cleared in Round 1: Aptitude");
			RoundResult wrong = result(round1, karan, RoundOutcome.NOT_CLEARED);
			existing.add(wrong);

			save(round1, item(karan, RoundOutcome.CLEARED));

			assertThat(wrong.getResult()).isEqualTo(RoundOutcome.CLEARED); // the same row was updated
			verify(resultRepository, never()).save(any()); // no second row
			assertThat(karan.getStatus()).isEqualTo(ApplicationStatus.SHORTLISTED);
			assertThat(karan.getStatusReason()).isNull();
			assertThat(auditedNewValue(12L)).containsEntry("status", "SHORTLISTED");
		}

		@Test
		void clearedCorrectedToAbsentRejects() {
			riya.setStatus(ApplicationStatus.SHORTLISTED);
			existing.add(result(round1, riya, RoundOutcome.CLEARED));

			save(round1, item(riya, RoundOutcome.ABSENT));

			assertThat(riya.getStatus()).isEqualTo(ApplicationStatus.REJECTED);
			assertThat(riya.getStatusReason()).isEqualTo("Not cleared in Round 1: Aptitude");
		}

		@Test
		void notClearedToAbsentKeepsTheSameStatusAndReasonSoNoAuditRow() {
			karan.setStatus(ApplicationStatus.REJECTED);
			karan.setStatusReason("Not cleared in Round 1: Aptitude");
			existing.add(result(round1, karan, RoundOutcome.NOT_CLEARED));

			save(round1, item(karan, RoundOutcome.ABSENT));

			verify(auditService, never()).record(any(), anyLong(), any(), any(), any());
		}

		@Test
		void failingAnEarlierRoundIsBlockedWhileALaterRoundHasAResult() {
			riya.setStatus(ApplicationStatus.SHORTLISTED);
			existing.add(result(round1, riya, RoundOutcome.CLEARED));
			existing.add(result(round2, riya, RoundOutcome.CLEARED));

			assertThatThrownBy(() -> save(round1, item(riya, RoundOutcome.NOT_CLEARED)))
					.isInstanceOfSatisfying(ConflictException.class, ex -> assertThat(ex.getDetails()).containsExactly(
							"Application 11 already has a result in Round 2: Technical, so it can't be marked "
									+ "NOT_CLEARED in Round 1: Aptitude"));
			assertThat(riya.getStatus()).isEqualTo(ApplicationStatus.SHORTLISTED);
		}

		@Test
		void clearedCanBeResentWhileALaterRoundHasAResult() {
			riya.setStatus(ApplicationStatus.SHORTLISTED);
			existing.add(result(round1, riya, RoundOutcome.CLEARED));
			existing.add(result(round2, riya, RoundOutcome.CLEARED));

			RoundResultsRequest.Item withRemarks = new RoundResultsRequest.Item(11L, RoundOutcome.CLEARED, "Top 10%");
			RoundResultsResponse response = service.saveResults(DRIVE_ID, 101L,
					new RoundResultsRequest(List.of(withRemarks)));

			assertThat(response.results().get(0).remarks()).isEqualTo("Top 10%");
			assertThat(riya.getStatus()).isEqualTo(ApplicationStatus.SHORTLISTED);
		}

	}

	// ---------------------------------------------------------------------

	@SuppressWarnings({ "unchecked", "rawtypes" })
	private Map<String, Object> auditedNewValue(Long applicationId) {
		ArgumentCaptor<Map<String, Object>> newValue = (ArgumentCaptor) ArgumentCaptor.forClass(Map.class);
		verify(auditService).record(eq(AuditEntityType.APPLICATION), eq(applicationId),
				eq(AuditAction.STATUS_CHANGED), any(), newValue.capture());
		return newValue.getValue();
	}

}
