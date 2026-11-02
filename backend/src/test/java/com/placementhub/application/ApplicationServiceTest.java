package com.placementhub.application;

import static com.placementhub.policy.PolicyFixtures.dreamOffer;
import static com.placementhub.policy.PolicyFixtures.fullTimeOffer;
import static com.placementhub.policy.PolicyFixtures.oneOffer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.hibernate.exception.ConstraintViolationException;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import com.placementhub.audit.AuditAction;
import com.placementhub.audit.AuditEntityType;
import com.placementhub.audit.AuditService;
import com.placementhub.common.Branch;
import com.placementhub.common.ConflictException;
import com.placementhub.common.ResourceNotFoundException;
import com.placementhub.common.UnprocessableEntityException;
import com.placementhub.company.Company;
import com.placementhub.drive.Drive;
import com.placementhub.drive.DriveRepository;
import com.placementhub.drive.DriveStatus;
import com.placementhub.drive.JobType;
import com.placementhub.policy.PlacementPolicyRepository;
import com.placementhub.policy.PolicyFixtures;
import com.placementhub.round.DriveRound;
import com.placementhub.round.DriveRoundRepository;
import com.placementhub.round.RoundFixtures;
import com.placementhub.round.RoundOutcome;
import com.placementhub.round.RoundResult;
import com.placementhub.round.RoundResultRepository;
import com.placementhub.student.CurrentStudentResolver;
import com.placementhub.student.StudentProfile;
import com.placementhub.user.Role;
import com.placementhub.user.User;

@ExtendWith(MockitoExtension.class)
class ApplicationServiceTest {

	private static final Instant NOW = Instant.parse("2026-11-01T10:00:00Z");
	private static final Long DRIVE_ID = 2L;
	private static final Long STUDENT_ID = 5L;
	private static final Long APPLICATION_ID = 50L;

	@Mock
	private CurrentStudentResolver currentStudentResolver;
	@Mock
	private DriveRepository driveRepository;
	@Mock
	private ApplicationRepository applicationRepository;
	@Mock
	private PlacementPolicyRepository policyRepository;
	@Mock
	private AuditService auditService;
	@Mock
	private DriveRoundRepository roundRepository;
	@Mock
	private RoundResultRepository resultRepository;

	private ApplicationService service;
	private StudentProfile aarav;
	private Drive drive;

	@BeforeEach
	void setUp() {
		service = new ApplicationService(currentStudentResolver, driveRepository, applicationRepository,
				new EligibilityService(), policyRepository, PolicyFixtures.engine(), new ApplicationMapper(),
				new StatusChanges(auditService), roundRepository, resultRepository, Clock.fixed(NOW, ZoneOffset.UTC));

		// Aarav: CSE, 8.70, 0 backlogs, 2027 -> eligible for the drive below
		aarav = new StudentProfile(new User("aarav.sharma@student.placementhub.dev", "hash", Role.STUDENT),
				"Aarav Sharma", "23CSE001", Branch.CSE, (short) 2027, new BigDecimal("8.70"), (short) 0);
		ReflectionTestUtils.setField(aarav, "id", STUDENT_ID);

		drive = new Drive(new Company("Nimbus Systems", null, null),
				new User("admin@placementhub.dev", "hash", Role.TPO_ADMIN));
		ReflectionTestUtils.setField(drive, "id", DRIVE_ID);
		drive.setRoleTitle("Software Engineer");
		drive.setJobType(JobType.FULL_TIME);
		drive.setCtcLpa(new BigDecimal("12.00"));
		drive.setMinCgpa(new BigDecimal("7.50"));
		drive.setMaxBacklogs((short) 0);
		drive.setGraduationYear((short) 2027);
		drive.setRegistrationDeadline(NOW.plus(Duration.ofDays(10)));
		drive.setBranches(Set.of(Branch.CSE, Branch.IT, Branch.ECE));
		drive.setStatus(DriveStatus.OPEN);

		// apply and withdraw use the locking lookup, reads the plain one; each test uses only one of them
		lenient().when(currentStudentResolver.requireProfile()).thenReturn(aarav);
		lenient().when(currentStudentResolver.requireProfileForUpdate()).thenReturn(aarav);
	}

	// =====================================================================
	// apply
	// =====================================================================

	@Nested
	class Apply {

		@Test
		void eligibleStudentGetsAppliedApplication() {
			when(driveRepository.findByIdForShare(DRIVE_ID)).thenReturn(Optional.of(drive));
			when(applicationRepository.findByDriveIdAndStudentId(DRIVE_ID, STUDENT_ID)).thenReturn(Optional.empty());
			when(applicationRepository.saveAndFlush(any(Application.class))).thenAnswer(inv -> inv.getArgument(0));

			ApplicationResponse response = service.apply(new ApplyRequest(DRIVE_ID));

			assertThat(response.status()).isEqualTo(ApplicationStatus.APPLIED);
			assertThat(response.driveId()).isEqualTo(DRIVE_ID);
			assertThat(response.companyName()).isEqualTo("Nimbus Systems");
			verify(applicationRepository).saveAndFlush(any(Application.class));
		}

		@Test
		void locksTheStudentRowAndAuditsTheNewApplication() {
			when(driveRepository.findByIdForShare(DRIVE_ID)).thenReturn(Optional.of(drive));
			when(applicationRepository.findByDriveIdAndStudentId(DRIVE_ID, STUDENT_ID)).thenReturn(Optional.empty());
			when(applicationRepository.saveAndFlush(any(Application.class))).thenAnswer(inv -> {
				Application saved = inv.getArgument(0);
				ReflectionTestUtils.setField(saved, "id", APPLICATION_ID);
				return saved;
			});

			service.apply(new ApplyRequest(DRIVE_ID));

			verify(currentStudentResolver).requireProfileForUpdate();
			verify(currentStudentResolver, never()).requireProfile();
			Map<String, Object> newValue = captureAudit(null);
			assertThat(newValue).containsEntry("status", "APPLIED").containsEntry("trigger", "APPLY");
		}

		@Test
		void takesASharedDriveLockBeforeTheStudentLock() {
			when(driveRepository.findByIdForShare(DRIVE_ID)).thenReturn(Optional.of(drive));
			when(applicationRepository.findByDriveIdAndStudentId(DRIVE_ID, STUDENT_ID)).thenReturn(Optional.empty());
			when(applicationRepository.saveAndFlush(any(Application.class))).thenAnswer(inv -> inv.getArgument(0));

			service.apply(new ApplyRequest(DRIVE_ID));

			InOrder order = inOrder(driveRepository, currentStudentResolver);
			order.verify(driveRepository).findByIdForShare(DRIVE_ID);
			order.verify(currentStudentResolver).requireProfileForUpdate();
			verify(driveRepository, never()).findById(any());
		}

		@Test
		void missingDriveIs404() {
			when(driveRepository.findByIdForShare(99L)).thenReturn(Optional.empty());

			assertThatThrownBy(() -> service.apply(new ApplyRequest(99L)))
					.isInstanceOf(ResourceNotFoundException.class)
					.hasMessage("Drive with id 99 not found");
			verify(applicationRepository, never()).saveAndFlush(any());
		}

		@Test
		void draftDriveIs404SoItsExistenceIsNotRevealed() {
			drive.setStatus(DriveStatus.DRAFT);
			when(driveRepository.findByIdForShare(DRIVE_ID)).thenReturn(Optional.of(drive));

			assertThatThrownBy(() -> service.apply(new ApplyRequest(DRIVE_ID)))
					.isInstanceOf(ResourceNotFoundException.class)
					.hasMessage("Drive with id 2 not found");
			verify(applicationRepository, never()).saveAndFlush(any());
		}

		@Test
		void ineligibleStudentGets422WithEveryReasonAndNothingIsSaved() {
			// Karan-like student: ME, 6.20, 2 backlogs
			StudentProfile karan = new StudentProfile(new User("k@student.placementhub.dev", "h", Role.STUDENT),
					"Karan Mehta", "23ME031", Branch.ME, (short) 2027, new BigDecimal("6.20"), (short) 2);
			ReflectionTestUtils.setField(karan, "id", 7L);
			when(currentStudentResolver.requireProfileForUpdate()).thenReturn(karan);
			when(driveRepository.findByIdForShare(DRIVE_ID)).thenReturn(Optional.of(drive));
			when(applicationRepository.findByDriveIdAndStudentId(DRIVE_ID, 7L)).thenReturn(Optional.empty());

			assertThatThrownBy(() -> service.apply(new ApplyRequest(DRIVE_ID)))
					.isInstanceOfSatisfying(UnprocessableEntityException.class, ex -> {
						assertThat(ex.getMessage()).isEqualTo("You are not eligible for this drive");
						assertThat(ex.getDetails()).containsExactly(
								"Open only to CSE, IT, ECE; your branch is ME",
								"Minimum CGPA is 7.50; yours is 6.20",
								"No active backlogs allowed; you have 2");
					});
			verify(applicationRepository, never()).saveAndFlush(any());
		}

		@Test
		void closedDriveGives422NotOpen() {
			drive.setStatus(DriveStatus.CLOSED);
			when(driveRepository.findByIdForShare(DRIVE_ID)).thenReturn(Optional.of(drive));
			when(applicationRepository.findByDriveIdAndStudentId(DRIVE_ID, STUDENT_ID)).thenReturn(Optional.empty());

			assertThatThrownBy(() -> service.apply(new ApplyRequest(DRIVE_ID)))
					.isInstanceOfSatisfying(UnprocessableEntityException.class, ex -> assertThat(ex.getDetails())
							.containsExactly("This drive is not open for applications (status: CLOSED)"));
		}

		@ParameterizedTest
		@EnumSource(value = ApplicationStatus.class, names = { "APPLIED", "SHORTLISTED", "REJECTED", "SELECTED",
				"INELIGIBLE" })
		void existingApplicationIs409AlreadyApplied(ApplicationStatus existingStatus) {
			when(driveRepository.findByIdForShare(DRIVE_ID)).thenReturn(Optional.of(drive));
			when(applicationRepository.findByDriveIdAndStudentId(DRIVE_ID, STUDENT_ID))
					.thenReturn(Optional.of(application(existingStatus)));

			assertThatThrownBy(() -> service.apply(new ApplyRequest(DRIVE_ID)))
					.isInstanceOf(ConflictException.class)
					.hasMessage("You have already applied to this drive");
			verify(applicationRepository, never()).saveAndFlush(any());
		}

		@Test
		void reApplyAfterWithdrawIs409WithdrawalIsFinal() {
			when(driveRepository.findByIdForShare(DRIVE_ID)).thenReturn(Optional.of(drive));
			when(applicationRepository.findByDriveIdAndStudentId(DRIVE_ID, STUDENT_ID))
					.thenReturn(Optional.of(application(ApplicationStatus.WITHDRAWN)));

			assertThatThrownBy(() -> service.apply(new ApplyRequest(DRIVE_ID)))
					.isInstanceOf(ConflictException.class)
					.hasMessage("You withdrew from this drive; withdrawal is final");
		}

		@Test
		void concurrentDuplicateCaughtByDbConstraintIs409() {
			// The check found nothing (the other request had not committed yet), but the INSERT hits UNIQUE
			when(driveRepository.findByIdForShare(DRIVE_ID)).thenReturn(Optional.of(drive));
			when(applicationRepository.findByDriveIdAndStudentId(DRIVE_ID, STUDENT_ID)).thenReturn(Optional.empty());
			when(applicationRepository.saveAndFlush(any(Application.class)))
					.thenThrow(constraintViolation("uq_applications_drive_student"));

			assertThatThrownBy(() -> service.apply(new ApplyRequest(DRIVE_ID)))
					.isInstanceOf(ConflictException.class)
					.hasMessage("You have already applied to this drive");
		}

		@Test
		void otherConstraintViolationsAreNotDisguisedAsDuplicates() {
			when(driveRepository.findByIdForShare(DRIVE_ID)).thenReturn(Optional.of(drive));
			when(applicationRepository.findByDriveIdAndStudentId(DRIVE_ID, STUDENT_ID)).thenReturn(Optional.empty());
			DataIntegrityViolationException other = constraintViolation("fk_applications_drive");
			when(applicationRepository.saveAndFlush(any(Application.class))).thenThrow(other);

			assertThatThrownBy(() -> service.apply(new ApplyRequest(DRIVE_ID))).isSameAs(other);
		}

	}

	// =====================================================================
	// apply: placement policies
	// =====================================================================

	@Nested
	class ApplyWithPolicies {

		@BeforeEach
		void eligibleStudentAndNoEarlierApplication() {
			when(driveRepository.findByIdForShare(DRIVE_ID)).thenReturn(Optional.of(drive));
			when(applicationRepository.findByDriveIdAndStudentId(DRIVE_ID, STUDENT_ID)).thenReturn(Optional.empty());
		}

		@Test
		void blockedByPolicyGets422WithTheReasonAndNothingIsSaved() {
			when(policyRepository.findByActiveTrue()).thenReturn(List.of(oneOffer(true)));
			when(applicationRepository.findSelectedOffers(STUDENT_ID))
					.thenReturn(List.of(fullTimeOffer("Kaveri Analytics", "6.50")));

			assertThatThrownBy(() -> service.apply(new ApplyRequest(DRIVE_ID)))
					.isInstanceOfSatisfying(UnprocessableEntityException.class, ex -> {
						assertThat(ex.getMessage()).isEqualTo("This application is blocked by a placement policy");
						assertThat(ex.getDetails()).containsExactly("One-offer policy: you already hold an offer of "
								+ "6.50 LPA from Kaveri Analytics; students with an offer cannot apply to more "
								+ "full-time drives");
					});
			verify(applicationRepository, never()).saveAndFlush(any());
		}

		@Test
		void ineligibleAndBlockedGivesOne422WithEveryReasonEligibilityFirst() {
			drive.setMinCgpa(new BigDecimal("9.00"));
			when(policyRepository.findByActiveTrue()).thenReturn(List.of(dreamOffer(true, "15.00")));
			when(applicationRepository.findSelectedOffers(STUDENT_ID))
					.thenReturn(List.of(fullTimeOffer("Kaveri Analytics", "6.00")));

			assertThatThrownBy(() -> service.apply(new ApplyRequest(DRIVE_ID)))
					.isInstanceOfSatisfying(UnprocessableEntityException.class, ex -> {
						assertThat(ex.getMessage()).isEqualTo("You are not eligible for this drive");
						assertThat(ex.getDetails()).containsExactly(
								"Minimum CGPA is 9.00; yours is 8.70",
								"Dream-offer policy: you hold an offer of 6.00 LPA from Kaveri Analytics; you may "
										+ "only apply to drives of 15.00 LPA or more (this drive offers 12.00 LPA)");
					});
			verify(applicationRepository, never()).saveAndFlush(any());
		}

		@Test
		void dreamUpgradeIsAllowedAndSaved() {
			// one 6.00 offer, threshold 10, this drive pays 12.00 -> the one allowed upgrade
			when(policyRepository.findByActiveTrue()).thenReturn(List.of(dreamOffer(true, "10.00")));
			when(applicationRepository.findSelectedOffers(STUDENT_ID))
					.thenReturn(List.of(fullTimeOffer("Kaveri Analytics", "6.00")));
			when(applicationRepository.saveAndFlush(any(Application.class))).thenAnswer(inv -> inv.getArgument(0));

			assertThat(service.apply(new ApplyRequest(DRIVE_ID)).status()).isEqualTo(ApplicationStatus.APPLIED);
		}

		@Test
		void internshipDriveSkipsThePolicyQueriesEntirely() {
			drive.setJobType(JobType.INTERNSHIP);
			when(applicationRepository.saveAndFlush(any(Application.class))).thenAnswer(inv -> inv.getArgument(0));

			assertThat(service.apply(new ApplyRequest(DRIVE_ID)).status()).isEqualTo(ApplicationStatus.APPLIED);
			verify(policyRepository, never()).findByActiveTrue();
			verify(applicationRepository, never()).findSelectedOffers(any());
		}

		@Test
		void noActivePolicySkipsTheOfferQuery() {
			when(policyRepository.findByActiveTrue()).thenReturn(List.of());
			when(applicationRepository.saveAndFlush(any(Application.class))).thenAnswer(inv -> inv.getArgument(0));

			assertThat(service.apply(new ApplyRequest(DRIVE_ID)).status()).isEqualTo(ApplicationStatus.APPLIED);
			verify(applicationRepository, never()).findSelectedOffers(any());
		}

	}

	// =====================================================================
	// withdraw
	// =====================================================================

	@Nested
	class Withdraw {

		@ParameterizedTest
		@EnumSource(value = ApplicationStatus.class, names = { "APPLIED", "SHORTLISTED" })
		void ownerCanWithdrawBeforeDeadlineWhileDriveIsOpen(ApplicationStatus status) {
			Application application = application(status);
			when(applicationRepository.findByIdAndStudentId(APPLICATION_ID, STUDENT_ID))
					.thenReturn(Optional.of(application));

			ApplicationResponse response = service.withdraw(APPLICATION_ID);

			assertThat(response.status()).isEqualTo(ApplicationStatus.WITHDRAWN);
			assertThat(response.driveStatus()).isEqualTo(DriveStatus.OPEN);
			assertThat(application.getStatus()).isEqualTo(ApplicationStatus.WITHDRAWN);
			verify(applicationRepository).flush();
		}

		@Test
		void locksTheStudentRowAndAuditsOldAndNewStatus() {
			when(applicationRepository.findByIdAndStudentId(APPLICATION_ID, STUDENT_ID))
					.thenReturn(Optional.of(application(ApplicationStatus.SHORTLISTED)));

			service.withdraw(APPLICATION_ID);

			verify(currentStudentResolver).requireProfileForUpdate();
			ArgumentCaptor<Map<String, Object>> oldValue = mapCaptor();
			ArgumentCaptor<Map<String, Object>> newValue = mapCaptor();
			verify(auditService).record(eq(AuditEntityType.APPLICATION), eq(APPLICATION_ID),
					eq(AuditAction.STATUS_CHANGED), oldValue.capture(), newValue.capture());
			assertThat(oldValue.getValue()).containsEntry("status", "SHORTLISTED");
			assertThat(newValue.getValue()).containsEntry("status", "WITHDRAWN").containsEntry("trigger", "WITHDRAW");
		}

		@Test
		void blockedWithdrawalWritesNoAudit() {
			when(applicationRepository.findByIdAndStudentId(APPLICATION_ID, STUDENT_ID))
					.thenReturn(Optional.of(application(ApplicationStatus.REJECTED)));

			assertThatThrownBy(() -> service.withdraw(APPLICATION_ID)).isInstanceOf(ConflictException.class);
			verify(auditService, never()).record(any(), anyLong(), any(), any(), any());
		}

		@Test
		void someoneElsesApplicationIs404NotForbidden() {
			// The query filters by owner, so another student's application is simply not found
			when(applicationRepository.findByIdAndStudentId(APPLICATION_ID, STUDENT_ID)).thenReturn(Optional.empty());

			assertThatThrownBy(() -> service.withdraw(APPLICATION_ID))
					.isInstanceOf(ResourceNotFoundException.class)
					.hasMessage("Application with id 50 not found");
		}

		@Test
		void blockedAfterDeadline() {
			drive.setRegistrationDeadline(NOW.minus(Duration.ofHours(1)));
			Application application = application(ApplicationStatus.APPLIED);
			when(applicationRepository.findByIdAndStudentId(APPLICATION_ID, STUDENT_ID))
					.thenReturn(Optional.of(application));

			assertThatThrownBy(() -> service.withdraw(APPLICATION_ID))
					.isInstanceOf(ConflictException.class)
					.hasMessage("Withdrawal closed on 1 Nov 2026, 2:30 PM IST (the registration deadline)");
			assertThat(application.getStatus()).isEqualTo(ApplicationStatus.APPLIED);
		}

		@Test
		void blockedWhenDeadlineIsExactlyNow() {
			drive.setRegistrationDeadline(NOW);
			when(applicationRepository.findByIdAndStudentId(APPLICATION_ID, STUDENT_ID))
					.thenReturn(Optional.of(application(ApplicationStatus.APPLIED)));

			assertThatThrownBy(() -> service.withdraw(APPLICATION_ID))
					.isInstanceOf(ConflictException.class)
					.hasMessageStartingWith("Withdrawal closed on");
		}

		@ParameterizedTest
		@EnumSource(value = DriveStatus.class, names = { "CLOSED", "COMPLETED" })
		void blockedWhenDriveIsNoLongerOpenEvenBeforeDeadline(DriveStatus driveStatus) {
			drive.setStatus(driveStatus); // deadline is still 10 days away
			Application application = application(ApplicationStatus.APPLIED);
			when(applicationRepository.findByIdAndStudentId(APPLICATION_ID, STUDENT_ID))
					.thenReturn(Optional.of(application));

			assertThatThrownBy(() -> service.withdraw(APPLICATION_ID))
					.isInstanceOf(ConflictException.class)
					.hasMessage("Withdrawal is not allowed: this drive is no longer open (status: " + driveStatus + ")");
			assertThat(application.getStatus()).isEqualTo(ApplicationStatus.APPLIED);
		}

		@Test
		void alreadyWithdrawnHasItsOwnMessage() {
			when(applicationRepository.findByIdAndStudentId(APPLICATION_ID, STUDENT_ID))
					.thenReturn(Optional.of(application(ApplicationStatus.WITHDRAWN)));

			assertThatThrownBy(() -> service.withdraw(APPLICATION_ID))
					.isInstanceOf(ConflictException.class)
					.hasMessage("You have already withdrawn this application");
		}

		@ParameterizedTest
		@EnumSource(value = ApplicationStatus.class, names = { "REJECTED", "SELECTED", "INELIGIBLE" })
		void blockedInFinalStatuses(ApplicationStatus status) {
			Application application = application(status);
			when(applicationRepository.findByIdAndStudentId(APPLICATION_ID, STUDENT_ID))
					.thenReturn(Optional.of(application));

			assertThatThrownBy(() -> service.withdraw(APPLICATION_ID))
					.isInstanceOf(ConflictException.class)
					.hasMessage("Cannot withdraw a " + status
							+ " application. Only APPLIED or SHORTLISTED applications can be withdrawn");
			assertThat(application.getStatus()).isEqualTo(status);
			verify(applicationRepository, never()).flush();
		}

	}

	// =====================================================================
	// read
	// =====================================================================

	@Test
	void getMineOfSomeoneElsesApplicationIs404() {
		when(applicationRepository.findByIdAndStudentId(APPLICATION_ID, STUDENT_ID)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.getMine(APPLICATION_ID))
				.isInstanceOf(ResourceNotFoundException.class)
				.hasMessage("Application with id 50 not found");
	}

	@Test
	void getMineTracksEveryRoundWithTheStudentsResultAndRemarks() {
		drive.setStatus(DriveStatus.CLOSED);
		Application application = application(ApplicationStatus.REJECTED);
		application.setStatusReason("Not cleared in Round 2: Technical");
		DriveRound round1 = RoundFixtures.round(101L, drive, 1, "Aptitude");
		DriveRound round2 = RoundFixtures.round(102L, drive, 2, "Technical");
		DriveRound round3 = RoundFixtures.round(103L, drive, 3, "HR");
		RoundResult cleared = RoundFixtures.result(round1, application, RoundOutcome.CLEARED);
		cleared.setRemarks("Good score");
		RoundResult failed = RoundFixtures.result(round2, application, RoundOutcome.NOT_CLEARED);
		when(applicationRepository.findByIdAndStudentId(APPLICATION_ID, STUDENT_ID))
				.thenReturn(Optional.of(application));
		when(roundRepository.findByDriveIdOrderByRoundNoAsc(DRIVE_ID)).thenReturn(List.of(round1, round2, round3));
		when(resultRepository.findByApplicationId(APPLICATION_ID)).thenReturn(List.of(failed, cleared));

		ApplicationDetailResponse response = service.getMine(APPLICATION_ID);

		assertThat(response.status()).isEqualTo(ApplicationStatus.REJECTED);
		assertThat(response.statusReason()).isEqualTo("Not cleared in Round 2: Technical");
		assertThat(response.driveStatus()).isEqualTo(DriveStatus.CLOSED);
		assertThat(response.rounds()).containsExactly(
				new ApplicationDetailResponse.Round(1, "Aptitude", null, RoundOutcome.CLEARED, "Good score"),
				new ApplicationDetailResponse.Round(2, "Technical", null, RoundOutcome.NOT_CLEARED, null),
				new ApplicationDetailResponse.Round(3, "HR", null, null, null)); // no result yet
	}

	@Test
	void getMineWithoutRoundsHasAnEmptyList() {
		when(applicationRepository.findByIdAndStudentId(APPLICATION_ID, STUDENT_ID))
				.thenReturn(Optional.of(application(ApplicationStatus.APPLIED)));
		when(roundRepository.findByDriveIdOrderByRoundNoAsc(DRIVE_ID)).thenReturn(List.of());
		when(resultRepository.findByApplicationId(APPLICATION_ID)).thenReturn(List.of());

		assertThat(service.getMine(APPLICATION_ID).rounds()).isEmpty();
	}

	// ---------------------------------------------------------------------
	// helpers
	// ---------------------------------------------------------------------

	/** The new value of the single audit row written; oldValue must be the expected old value (null on apply). */
	private Map<String, Object> captureAudit(Map<String, Object> expectedOldValue) {
		ArgumentCaptor<Map<String, Object>> newValue = mapCaptor();
		verify(auditService).record(eq(AuditEntityType.APPLICATION), eq(APPLICATION_ID),
				eq(AuditAction.STATUS_CHANGED), expectedOldValue == null ? isNull() : eq(expectedOldValue),
				newValue.capture());
		return newValue.getValue();
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	private static ArgumentCaptor<Map<String, Object>> mapCaptor() {
		return (ArgumentCaptor) ArgumentCaptor.forClass(Map.class);
	}

	private Application application(ApplicationStatus status) {
		Application application = new Application(drive, aarav);
		ReflectionTestUtils.setField(application, "id", APPLICATION_ID);
		application.setStatus(status);
		return application;
	}

	/** What Spring throws when Postgres rejects an INSERT because of the named constraint. */
	private static DataIntegrityViolationException constraintViolation(String constraintName) {
		ConstraintViolationException hibernate = new ConstraintViolationException(
				"duplicate key value violates unique constraint \"" + constraintName + "\"",
				new SQLException("23505"), constraintName);
		return new DataIntegrityViolationException("could not execute statement", hibernate);
	}

}
