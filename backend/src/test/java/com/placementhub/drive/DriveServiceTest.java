package com.placementhub.drive;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.placementhub.application.CriteriaChangeService;
import com.placementhub.application.SelectionService;
import com.placementhub.audit.AuditAction;
import com.placementhub.audit.AuditEntityType;
import com.placementhub.audit.AuditService;
import com.placementhub.common.Branch;
import com.placementhub.common.ConflictException;
import com.placementhub.common.ResourceNotFoundException;
import com.placementhub.company.Company;
import com.placementhub.company.CompanyRepository;
import com.placementhub.user.CurrentUserProvider;
import com.placementhub.user.Role;
import com.placementhub.user.User;
import com.placementhub.user.UserRepository;

@ExtendWith(MockitoExtension.class)
class DriveServiceTest {

	private static final Instant NOW = Instant.parse("2026-11-01T10:00:00Z");
	private static final Instant FUTURE = NOW.plus(Duration.ofDays(10));
	private static final Instant PAST = NOW.minus(Duration.ofDays(1));
	private static final Long DRIVE_ID = 7L;

	@Mock
	private DriveRepository driveRepository;
	@Mock
	private CompanyRepository companyRepository;
	@Mock
	private UserRepository userRepository;
	@Mock
	private CurrentUserProvider currentUserProvider;
	@Mock
	private SelectionService selectionService;
	@Mock
	private CriteriaChangeService criteriaChangeService;
	@Mock
	private AuditService auditService;

	private DriveService driveService;
	private final Company company = new Company("Nimbus Systems", null, null);
	private final User admin = new User("admin@placementhub.dev", "hash", Role.TPO_ADMIN);

	@BeforeEach
	void setUp() {
		Clock fixedClock = Clock.fixed(NOW, ZoneOffset.UTC);
		driveService = new DriveService(driveRepository, companyRepository, userRepository, currentUserProvider,
				new DriveMapper(), selectionService, criteriaChangeService, auditService, fixedClock);
	}

	// ---------------------------------------------------------------------
	// Status transitions: all 12 (from, to) combinations
	// ---------------------------------------------------------------------

	/** Written out by hand (not read from the service) so the test checks the rules independently. */
	private static final Set<String> EXPECTED_ALLOWED = Set.of("DRAFT->OPEN", "OPEN->CLOSED", "CLOSED->COMPLETED");

	static Stream<Arguments> allTransitions() {
		Set<DriveStatus> targets = EnumSet.of(DriveStatus.OPEN, DriveStatus.CLOSED, DriveStatus.COMPLETED);
		return Stream.of(DriveStatus.values())
				.flatMap(from -> targets.stream().map(to -> Arguments.of(from, to)));
	}

	@ParameterizedTest(name = "{0} -> {1}")
	@MethodSource("allTransitions")
	@DisplayName("only DRAFT->OPEN, OPEN->CLOSED and CLOSED->COMPLETED are allowed")
	void statusTransitions(DriveStatus from, DriveStatus to) {
		Drive drive = validDrive(from);
		when(driveRepository.findByIdForUpdate(DRIVE_ID)).thenReturn(Optional.of(drive));

		boolean shouldBeAllowed = EXPECTED_ALLOWED.contains(from + "->" + to);

		if (shouldBeAllowed) {
			DriveResponse response = transition(to);
			assertThat(response.status()).isEqualTo(to);
			assertThat(drive.getStatus()).isEqualTo(to);
		} else {
			assertThatThrownBy(() -> transition(to))
					.isInstanceOf(ConflictException.class)
					.hasMessageStartingWith("Cannot change drive status from " + from + " to " + to);
			assertThat(drive.getStatus()).as("status must not change").isEqualTo(from);
			verify(driveRepository, never()).flush();
		}
	}

	@Test
	void blockedTransitionMessageNamesTheAllowedOne() {
		when(driveRepository.findByIdForUpdate(DRIVE_ID)).thenReturn(Optional.of(validDrive(DriveStatus.OPEN)));

		assertThatThrownBy(() -> driveService.complete(DRIVE_ID))
				.isInstanceOf(ConflictException.class)
				.hasMessage("Cannot change drive status from OPEN to COMPLETED. Allowed: OPEN → CLOSED");
	}

	@Test
	void completingRejectsThePendingApplicationsInTheSameTransaction() {
		Drive drive = validDrive(DriveStatus.CLOSED);
		when(driveRepository.findByIdForUpdate(DRIVE_ID)).thenReturn(Optional.of(drive));

		driveService.complete(DRIVE_ID);

		assertThat(drive.getStatus()).isEqualTo(DriveStatus.COMPLETED);
		verify(selectionService).rejectPending(drive);
	}

	@Test
	void blockedCompletionRejectsNothing() {
		when(driveRepository.findByIdForUpdate(DRIVE_ID)).thenReturn(Optional.of(validDrive(DriveStatus.OPEN)));

		assertThatThrownBy(() -> driveService.complete(DRIVE_ID)).isInstanceOf(ConflictException.class);
		verify(selectionService, never()).rejectPending(any());
	}

	@Test
	void completedIsFinal() {
		when(driveRepository.findByIdForUpdate(DRIVE_ID)).thenReturn(Optional.of(validDrive(DriveStatus.COMPLETED)));

		assertThatThrownBy(() -> driveService.open(DRIVE_ID))
				.isInstanceOf(ConflictException.class)
				.hasMessageEndingWith("Allowed: none, COMPLETED is final");
	}

	@Test
	void transitionOnMissingDriveIs404() {
		when(driveRepository.findByIdForUpdate(DRIVE_ID)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> driveService.open(DRIVE_ID))
				.isInstanceOf(ResourceNotFoundException.class)
				.hasMessage("Drive with id 7 not found");
	}

	// ---------------------------------------------------------------------
	// Preconditions for opening
	// ---------------------------------------------------------------------

	@Nested
	class Opening {

		@Test
		void blockedWhenDeadlineHasPassed() {
			Drive drive = validDrive(DriveStatus.DRAFT);
			drive.setRegistrationDeadline(PAST);
			when(driveRepository.findByIdForUpdate(DRIVE_ID)).thenReturn(Optional.of(drive));

			assertThatThrownBy(() -> driveService.open(DRIVE_ID))
					.isInstanceOf(ConflictException.class)
					.hasMessage("Cannot open drive: registration deadline must be in the future");
			assertThat(drive.getStatus()).isEqualTo(DriveStatus.DRAFT);
		}

		@Test
		void blockedWhenDeadlineIsExactlyNow() {
			Drive drive = validDrive(DriveStatus.DRAFT);
			drive.setRegistrationDeadline(NOW);
			when(driveRepository.findByIdForUpdate(DRIVE_ID)).thenReturn(Optional.of(drive));

			assertThatThrownBy(() -> driveService.open(DRIVE_ID))
					.isInstanceOf(ConflictException.class)
					.hasMessageContaining("deadline must be in the future");
		}

		@Test
		void blockedWhenNoBranches() {
			Drive drive = validDrive(DriveStatus.DRAFT);
			drive.setBranches(Set.of());
			when(driveRepository.findByIdForUpdate(DRIVE_ID)).thenReturn(Optional.of(drive));

			assertThatThrownBy(() -> driveService.open(DRIVE_ID))
					.isInstanceOf(ConflictException.class)
					.hasMessage("Cannot open drive: select at least one eligible branch");
			assertThat(drive.getStatus()).isEqualTo(DriveStatus.DRAFT);
		}

	}

	// ---------------------------------------------------------------------
	// Editing
	// ---------------------------------------------------------------------

	@Nested
	class Editing {

		@ParameterizedTest
		@EnumSource(value = DriveStatus.class, names = { "DRAFT", "OPEN" })
		void allowedInDraftAndOpen(DriveStatus status) {
			when(driveRepository.findByIdForUpdate(DRIVE_ID)).thenReturn(Optional.of(validDrive(status)));

			DriveResponse response = driveService.update(DRIVE_ID, updateRequest(FUTURE, Set.of(Branch.CSE)));

			assertThat(response.roleTitle()).isEqualTo("Updated Role");
			assertThat(response.status()).isEqualTo(status);
		}

		@ParameterizedTest
		@EnumSource(value = DriveStatus.class, names = { "CLOSED", "COMPLETED" })
		void blockedInClosedAndCompleted(DriveStatus status) {
			Drive drive = validDrive(status);
			when(driveRepository.findByIdForUpdate(DRIVE_ID)).thenReturn(Optional.of(drive));

			assertThatThrownBy(() -> driveService.update(DRIVE_ID, updateRequest(FUTURE, Set.of(Branch.CSE))))
					.isInstanceOf(ConflictException.class)
					.hasMessage("Cannot edit a " + status + " drive. Only DRAFT or OPEN drives can be edited");
			assertThat(drive.getRoleTitle()).as("fields must not change").isEqualTo("Software Engineer");
		}

		@Test
		void openDriveCannotBeEditedToPastDeadline() {
			when(driveRepository.findByIdForUpdate(DRIVE_ID)).thenReturn(Optional.of(validDrive(DriveStatus.OPEN)));

			assertThatThrownBy(() -> driveService.update(DRIVE_ID, updateRequest(PAST, Set.of(Branch.CSE))))
					.isInstanceOf(ConflictException.class)
					.hasMessage("Cannot save OPEN drive: registration deadline must be in the future");
		}

		@Test
		void openDriveCannotLoseAllBranches() {
			when(driveRepository.findByIdForUpdate(DRIVE_ID)).thenReturn(Optional.of(validDrive(DriveStatus.OPEN)));

			assertThatThrownBy(() -> driveService.update(DRIVE_ID, updateRequest(FUTURE, Set.of())))
					.isInstanceOf(ConflictException.class)
					.hasMessage("Cannot save OPEN drive: select at least one eligible branch");
		}

		@Test
		void draftDriveMayHavePastDeadlineAndNoBranches() {
			when(driveRepository.findByIdForUpdate(DRIVE_ID)).thenReturn(Optional.of(validDrive(DriveStatus.DRAFT)));

			DriveResponse response = driveService.update(DRIVE_ID, updateRequest(PAST, Set.of()));

			assertThat(response.branches()).isEmpty();
		}

	}

	// ---------------------------------------------------------------------
	// Criteria changes: jobType rule, re-evaluation, audit
	// ---------------------------------------------------------------------

	@Nested
	class CriteriaChanges {

		@Test
		void editingLocksTheDriveRowInsteadOfAPlainRead() {
			when(driveRepository.findByIdForUpdate(DRIVE_ID)).thenReturn(Optional.of(validDrive(DriveStatus.OPEN)));

			driveService.update(DRIVE_ID, sameAsValidDrive().withMinCgpa("8.00").build());

			verify(driveRepository).findByIdForUpdate(DRIVE_ID);
			verify(driveRepository, never()).findById(any());
		}

		@Test
		void jobTypeCannotChangeOnceTheDriveIsOpen() {
			Drive drive = validDrive(DriveStatus.OPEN);
			when(driveRepository.findByIdForUpdate(DRIVE_ID)).thenReturn(Optional.of(drive));

			assertThatThrownBy(() -> driveService.update(DRIVE_ID,
					sameAsValidDrive().withJobType(JobType.INTERNSHIP).build()))
					.isInstanceOf(ConflictException.class)
					.hasMessageStartingWith("jobType can be changed only while the drive is DRAFT (status: OPEN)");
			assertThat(drive.getJobType()).isEqualTo(JobType.FULL_TIME);
			verifyNoInteractions(criteriaChangeService, auditService);
		}

		@Test
		void jobTypeCanChangeWhileDraft() {
			when(driveRepository.findByIdForUpdate(DRIVE_ID)).thenReturn(Optional.of(validDrive(DriveStatus.DRAFT)));

			DriveResponse response = driveService.update(DRIVE_ID,
					sameAsValidDrive().withJobType(JobType.INTERNSHIP).build());

			assertThat(response.jobType()).isEqualTo(JobType.INTERNSHIP);
		}

		@Test
		void criteriaChangeOnAnOpenDriveReChecksWithTheNewCriteriaAndReturnsTheCount() {
			Drive drive = validDrive(DriveStatus.OPEN);
			when(driveRepository.findByIdForUpdate(DRIVE_ID)).thenReturn(Optional.of(drive));
			// the re-check must see the NEW minimum, not the old one
			when(criteriaChangeService.reevaluate(drive)).thenAnswer(inv -> {
				assertThat(drive.getMinCgpa()).isEqualByComparingTo("8.00");
				return 2;
			});

			DriveResponse response = driveService.update(DRIVE_ID, sameAsValidDrive().withMinCgpa("8.00").build());

			assertThat(response.applicationsMadeIneligible()).isEqualTo(2);
		}

		@Test
		void theReCheckRunsAfterTheDriveLockAndBeforeTheAudit() {
			Drive drive = validDrive(DriveStatus.OPEN);
			when(driveRepository.findByIdForUpdate(DRIVE_ID)).thenReturn(Optional.of(drive));

			driveService.update(DRIVE_ID, sameAsValidDrive().withBranches(Set.of(Branch.CSE)).build());

			InOrder order = inOrder(driveRepository, criteriaChangeService, auditService);
			order.verify(driveRepository).findByIdForUpdate(DRIVE_ID);
			order.verify(criteriaChangeService).reevaluate(drive);
			order.verify(auditService).record(any(), any(), any(), any(), any());
		}

		@ParameterizedTest(name = "{0}")
		@MethodSource("com.placementhub.drive.DriveServiceTest#eligibilityEdits")
		void everyEligibilityFieldTriggersTheReCheck(String field, UpdateBuilder edit) {
			Drive drive = validDrive(DriveStatus.OPEN);
			when(driveRepository.findByIdForUpdate(DRIVE_ID)).thenReturn(Optional.of(drive));

			driveService.update(DRIVE_ID, edit.build());

			verify(criteriaChangeService).reevaluate(drive);
		}

		@Test
		void nonCriteriaEditsAreAuditedButReCheckNothing() {
			when(driveRepository.findByIdForUpdate(DRIVE_ID)).thenReturn(Optional.of(validDrive(DriveStatus.OPEN)));

			DriveResponse response = driveService.update(DRIVE_ID,
					sameAsValidDrive().withRoleTitle("SDE 1").withCtc("14.00").withDeadline(FUTURE.plusSeconds(60))
							.build());

			verifyNoInteractions(criteriaChangeService);
			assertThat(response.applicationsMadeIneligible()).isZero();
			AuditRow row = auditedEdit();
			assertThat(row.oldValue()).containsOnlyKeys("roleTitle", "ctcLpa", "registrationDeadline")
					.containsEntry("roleTitle", "Software Engineer").containsEntry("ctcLpa", new BigDecimal("12.00"))
					.containsEntry("registrationDeadline", FUTURE.toString());
			assertThat(row.newValue()).containsEntry("roleTitle", "SDE 1")
					.containsEntry("ctcLpa", new BigDecimal("14.00")).containsEntry("applicationsMadeIneligible", 0);
		}

		@Test
		void criteriaChangeOnADraftDriveIsAuditedButReChecksNothing() {
			when(driveRepository.findByIdForUpdate(DRIVE_ID)).thenReturn(Optional.of(validDrive(DriveStatus.DRAFT)));

			driveService.update(DRIVE_ID, sameAsValidDrive().withMinCgpa("9.00").build());

			verifyNoInteractions(criteriaChangeService);
			assertThat(auditedEdit().oldValue()).containsOnlyKeys("minCgpa");
		}

		@Test
		void theAuditHoldsOnlyTheChangedFieldsOldToNewWithTheEffect() {
			Drive drive = validDrive(DriveStatus.OPEN);
			when(driveRepository.findByIdForUpdate(DRIVE_ID)).thenReturn(Optional.of(drive));
			when(criteriaChangeService.reevaluate(drive)).thenReturn(3);

			driveService.update(DRIVE_ID,
					sameAsValidDrive().withMinCgpa("8.00").withBranches(Set.of(Branch.CSE)).build());

			AuditRow row = auditedEdit();
			assertThat(row.oldValue()).containsOnlyKeys("minCgpa", "branches")
					.containsEntry("minCgpa", new BigDecimal("7.50"))
					.containsEntry("branches", List.of("CSE", "ECE"));
			assertThat(row.newValue()).containsOnlyKeys("minCgpa", "branches", "applicationsMadeIneligible")
					.containsEntry("minCgpa", new BigDecimal("8.00"))
					.containsEntry("branches", List.of("CSE"))
					.containsEntry("applicationsMadeIneligible", 3);
		}

		@Test
		void anEditThatChangesNothingWritesNoAuditAndReChecksNothing() {
			when(driveRepository.findByIdForUpdate(DRIVE_ID)).thenReturn(Optional.of(validDrive(DriveStatus.OPEN)));

			// "12" and "7.5" are the stored 12.00 and 7.50: the same values
			DriveResponse response = driveService.update(DRIVE_ID,
					sameAsValidDrive().withCtc("12").withMinCgpa("7.5").build());

			verifyNoInteractions(criteriaChangeService, auditService);
			assertThat(response.applicationsMadeIneligible()).isZero();
		}

		@Test
		void anInvalidEditReChecksAndAuditsNothing() {
			when(driveRepository.findByIdForUpdate(DRIVE_ID)).thenReturn(Optional.of(validDrive(DriveStatus.OPEN)));

			assertThatThrownBy(() -> driveService.update(DRIVE_ID,
					sameAsValidDrive().withMinCgpa("9.00").withDeadline(PAST).build()))
					.isInstanceOf(ConflictException.class);
			verifyNoInteractions(criteriaChangeService, auditService);
		}

		@SuppressWarnings({ "unchecked", "rawtypes" })
		private AuditRow auditedEdit() {
			ArgumentCaptor<Map<String, Object>> oldValue = (ArgumentCaptor) ArgumentCaptor.forClass(Map.class);
			ArgumentCaptor<Map<String, Object>> newValue = (ArgumentCaptor) ArgumentCaptor.forClass(Map.class);
			verify(auditService).record(eq(AuditEntityType.DRIVE), any(), eq(AuditAction.CRITERIA_CHANGED),
					oldValue.capture(), newValue.capture());
			return new AuditRow(oldValue.getValue(), newValue.getValue());
		}

	}

	private record AuditRow(Map<String, Object> oldValue, Map<String, Object> newValue) {
	}

	static Stream<Arguments> eligibilityEdits() {
		return Stream.of(
				Arguments.of("branches", sameAsValidDrive().withBranches(Set.of(Branch.CSE))),
				Arguments.of("minCgpa", sameAsValidDrive().withMinCgpa("8.00")),
				Arguments.of("maxBacklogs", sameAsValidDrive().withMaxBacklogs(2)),
				Arguments.of("graduationYear", sameAsValidDrive().withGraduationYear(2028)));
	}

	/** Builds an update request. Starts from the exact values of validDrive(), so "no change" is easy to write. */
	record UpdateBuilder(String roleTitle, JobType jobType, String ctc, String minCgpa, int maxBacklogs,
			int graduationYear, Instant deadline, Set<Branch> branches) {

		UpdateBuilder withRoleTitle(String v) {
			return new UpdateBuilder(v, jobType, ctc, minCgpa, maxBacklogs, graduationYear, deadline, branches);
		}

		UpdateBuilder withJobType(JobType v) {
			return new UpdateBuilder(roleTitle, v, ctc, minCgpa, maxBacklogs, graduationYear, deadline, branches);
		}

		UpdateBuilder withCtc(String v) {
			return new UpdateBuilder(roleTitle, jobType, v, minCgpa, maxBacklogs, graduationYear, deadline, branches);
		}

		UpdateBuilder withMinCgpa(String v) {
			return new UpdateBuilder(roleTitle, jobType, ctc, v, maxBacklogs, graduationYear, deadline, branches);
		}

		UpdateBuilder withMaxBacklogs(int v) {
			return new UpdateBuilder(roleTitle, jobType, ctc, minCgpa, v, graduationYear, deadline, branches);
		}

		UpdateBuilder withGraduationYear(int v) {
			return new UpdateBuilder(roleTitle, jobType, ctc, minCgpa, maxBacklogs, v, deadline, branches);
		}

		UpdateBuilder withDeadline(Instant v) {
			return new UpdateBuilder(roleTitle, jobType, ctc, minCgpa, maxBacklogs, graduationYear, v, branches);
		}

		UpdateBuilder withBranches(Set<Branch> v) {
			return new UpdateBuilder(roleTitle, jobType, ctc, minCgpa, maxBacklogs, graduationYear, deadline, v);
		}

		DriveUpdateRequest build() {
			return new DriveUpdateRequest(roleTitle, jobType, new BigDecimal(ctc), null, null,
					new BigDecimal(minCgpa), maxBacklogs, graduationYear, deadline, null, branches);
		}

	}

	private static UpdateBuilder sameAsValidDrive() {
		return new UpdateBuilder("Software Engineer", JobType.FULL_TIME, "12.00", "7.50", 0, 2027, FUTURE,
				Set.of(Branch.CSE, Branch.ECE));
	}

	// ---------------------------------------------------------------------
	// Creating
	// ---------------------------------------------------------------------

	@Nested
	class Creating {

		@Test
		void alwaysStartsAsDraftAndRecordsTheCreator() {
			when(companyRepository.findById(1L)).thenReturn(Optional.of(company));
			when(currentUserProvider.currentUserId()).thenReturn(42L);
			when(userRepository.getReferenceById(42L)).thenReturn(admin);
			when(driveRepository.save(any(Drive.class))).thenAnswer(inv -> inv.getArgument(0));

			DriveResponse response = driveService.create(createRequest(1L, LocalDate.of(2026, 12, 1)));

			assertThat(response.status()).isEqualTo(DriveStatus.DRAFT);
			assertThat(response.companyName()).isEqualTo("Nimbus Systems");
			assertThat(response.branches()).containsExactly(Branch.CSE, Branch.IT); // sorted
			verify(driveRepository).save(any(Drive.class));
		}

		@Test
		void unknownCompanyIs404AndNothingIsSaved() {
			when(companyRepository.findById(999L)).thenReturn(Optional.empty());

			assertThatThrownBy(() -> driveService.create(createRequest(999L, null)))
					.isInstanceOf(ResourceNotFoundException.class)
					.hasMessage("Company with id 999 not found");
			verify(driveRepository, never()).save(any());
		}

		@Test
		void driveDateBeforeDeadlineIsRejected() {
			when(companyRepository.findById(1L)).thenReturn(Optional.of(company));
			when(currentUserProvider.currentUserId()).thenReturn(42L);
			when(userRepository.getReferenceById(42L)).thenReturn(admin);

			// deadline is NOW + 10 days (2026-11-11); drive date 2026-11-05 is earlier
			assertThatThrownBy(() -> driveService.create(createRequest(1L, LocalDate.of(2026, 11, 5))))
					.isInstanceOf(ConflictException.class)
					.hasMessage("Drive date cannot be before the registration deadline");
			verify(driveRepository, never()).save(any());
		}

	}

	// ---------------------------------------------------------------------
	// helpers
	// ---------------------------------------------------------------------

	private DriveResponse transition(DriveStatus target) {
		return switch (target) {
			case OPEN -> driveService.open(DRIVE_ID);
			case CLOSED -> driveService.close(DRIVE_ID);
			case COMPLETED -> driveService.complete(DRIVE_ID);
			case DRAFT -> throw new IllegalArgumentException("no endpoint moves a drive back to DRAFT");
		};
	}

	/** A drive that passes every "can open" check, in the given status. */
	private Drive validDrive(DriveStatus status) {
		Drive drive = new Drive(company, admin);
		drive.setRoleTitle("Software Engineer");
		drive.setJobType(JobType.FULL_TIME);
		drive.setCtcLpa(new BigDecimal("12.00"));
		drive.setMinCgpa(new BigDecimal("7.50"));
		drive.setMaxBacklogs((short) 0);
		drive.setGraduationYear((short) 2027);
		drive.setRegistrationDeadline(FUTURE);
		drive.setBranches(Set.of(Branch.CSE, Branch.ECE));
		drive.setStatus(status);
		return drive;
	}

	private static DriveUpdateRequest updateRequest(Instant deadline, Set<Branch> branches) {
		return new DriveUpdateRequest("Updated Role", JobType.FULL_TIME, new BigDecimal("10.00"), "Pune", null,
				new BigDecimal("7.00"), 1, 2027, deadline, null, branches);
	}

	private static DriveCreateRequest createRequest(Long companyId, LocalDate driveDate) {
		return new DriveCreateRequest(companyId, "Software Engineer", JobType.FULL_TIME, new BigDecimal("12.00"),
				"Bengaluru", null, new BigDecimal("7.50"), 0, 2027, FUTURE, driveDate, Set.of(Branch.IT, Branch.CSE));
	}

}
