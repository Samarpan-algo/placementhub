package com.placementhub.application;

import static com.placementhub.policy.PolicyFixtures.dreamOffer;
import static com.placementhub.policy.PolicyFixtures.fullTimeOffer;
import static com.placementhub.policy.PolicyFixtures.maxOffers;
import static com.placementhub.policy.PolicyFixtures.oneOffer;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
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
import com.placementhub.policy.PlacementPolicyRepository;
import com.placementhub.policy.PolicyFixtures;
import com.placementhub.round.DriveRound;
import com.placementhub.round.DriveRoundRepository;
import com.placementhub.round.RoundOutcome;
import com.placementhub.round.RoundResultRepository;
import com.placementhub.student.StudentProfile;
import com.placementhub.student.StudentProfileRepository;

@ExtendWith(MockitoExtension.class)
class SelectionServiceTest {

	private static final Long DRIVE_ID = 7L;

	@Mock
	private DriveRepository driveRepository;
	@Mock
	private ApplicationRepository applicationRepository;
	@Mock
	private StudentProfileRepository studentProfileRepository;
	@Mock
	private DriveRoundRepository roundRepository;
	@Mock
	private RoundResultRepository resultRepository;
	@Mock
	private PlacementPolicyRepository policyRepository;
	@Mock
	private AuditService auditService;

	private SelectionService service;

	/** The drive being selected for: Nimbus, FULL_TIME, 12 LPA, CLOSED. */
	private Drive nimbus;
	private Application riyaNimbus;
	private Application karanNimbus;
	/** Riya's other pending FULL_TIME applications: Orbit 8 LPA (APPLIED), Zenith 15 LPA (SHORTLISTED). */
	private Application riyaOrbit;
	private Application riyaZenith;

	@BeforeEach
	void setUp() {
		service = new SelectionService(driveRepository, applicationRepository, studentProfileRepository,
				roundRepository, resultRepository, policyRepository, PolicyFixtures.engine(),
				new StatusChanges(auditService));

		nimbus = drive(DRIVE_ID, "Nimbus Systems", JobType.FULL_TIME, "12", DriveStatus.CLOSED);
		StudentProfile riya = student(1L, "Riya");
		StudentProfile karan = student(2L, "Karan");
		riyaNimbus = application(11L, nimbus, riya, ApplicationStatus.SHORTLISTED);
		karanNimbus = application(12L, nimbus, karan, ApplicationStatus.APPLIED);
		riyaOrbit = application(21L, drive(8L, "Orbit Labs", JobType.FULL_TIME, "8", DriveStatus.OPEN), riya,
				ApplicationStatus.APPLIED);
		riyaZenith = application(22L, drive(9L, "Zenith Corp", JobType.FULL_TIME, "15", DriveStatus.CLOSED), riya,
				ApplicationStatus.SHORTLISTED);

		lenient().when(driveRepository.findByIdForUpdate(DRIVE_ID)).thenReturn(Optional.of(nimbus));
		lenient().when(applicationRepository.findStudentIdsByIdInAndDriveId(anyCollection(), eq(DRIVE_ID)))
				.thenReturn(List.of(1L, 2L));
		lenient().when(applicationRepository.findByIdInAndDriveId(anyCollection(), eq(DRIVE_ID)))
				.thenAnswer(inv -> {
					Collection<Long> ids = inv.getArgument(0);
					return List.of(riyaNimbus, karanNimbus).stream().filter(a -> ids.contains(a.getId())).toList();
				});
		lenient().when(applicationRepository.findPendingFullTimeByStudentId(1L, 11L))
				.thenReturn(List.of(riyaOrbit, riyaZenith));
		lenient().when(applicationRepository.findSelectedOffers(anyLong())).thenReturn(List.of());
		lenient().when(policyRepository.findByActiveTrue()).thenReturn(List.of());
	}

	private SelectionResponse select(Long... ids) {
		return service.select(DRIVE_ID, new SelectionRequest(List.of(ids)));
	}

	// =====================================================================
	// basic selection rules
	// =====================================================================

	@Nested
	class Rules {

		@Test
		void appliedAndShortlistedBecomeSelectedAndAreAudited() {
			SelectionResponse response = select(11L, 12L);

			assertThat(riyaNimbus.getStatus()).isEqualTo(ApplicationStatus.SELECTED);
			assertThat(karanNimbus.getStatus()).isEqualTo(ApplicationStatus.SELECTED);
			assertThat(response.selected()).extracting(SelectionResponse.Selected::studentName)
					.containsExactly("Riya", "Karan");
			assertThat(response.madeIneligible()).isEmpty();
			assertThat(auditedNewValue(11L)).containsEntry("status", "SELECTED").containsEntry("trigger", "SELECTION");
		}

		@Test
		void noActivePolicyMeansNoReEvaluationQueries() {
			select(11L);

			verify(applicationRepository, never()).findSelectedOffers(anyLong());
			verify(applicationRepository, never()).findPendingFullTimeByStudentId(anyLong(), anyLong());
		}

		@ParameterizedTest
		@EnumSource(value = DriveStatus.class, names = { "DRAFT", "OPEN", "COMPLETED" })
		void driveMustBeClosed(DriveStatus status) {
			nimbus.setStatus(status);

			assertThatThrownBy(() -> select(11L))
					.isInstanceOf(ConflictException.class)
					.hasMessage("Selections can be made only while the drive is CLOSED (status: " + status + ")");
		}

		@Test
		void missingDriveIs404() {
			when(driveRepository.findByIdForUpdate(99L)).thenReturn(Optional.empty());

			assertThatThrownBy(() -> service.select(99L, new SelectionRequest(List.of(11L))))
					.isInstanceOf(ResourceNotFoundException.class);
		}

		@Test
		void ineligibleCannotBeSelectedAndTheReasonIsShown() {
			karanNimbus.setStatus(ApplicationStatus.INELIGIBLE);
			karanNimbus.setStatusReason("One-offer policy: you already hold an offer of 9.00 LPA from Orbit Labs; "
					+ "students with an offer cannot apply to more full-time drives");

			assertThatThrownBy(() -> select(12L))
					.isInstanceOfSatisfying(ConflictException.class, ex -> assertThat(ex.getDetails()).containsExactly(
							"Application 12 is INELIGIBLE and can't be selected: One-offer policy: you already hold "
									+ "an offer of 9.00 LPA from Orbit Labs; students with an offer cannot apply to "
									+ "more full-time drives"));
		}

		@ParameterizedTest
		@EnumSource(value = ApplicationStatus.class, names = { "REJECTED", "WITHDRAWN" })
		void onlyAppliedOrShortlistedCanBeSelected(ApplicationStatus status) {
			karanNimbus.setStatus(status);

			assertThatThrownBy(() -> select(12L))
					.isInstanceOfSatisfying(ConflictException.class, ex -> assertThat(ex.getDetails()).containsExactly(
							"Application 12 is " + status
									+ "; only APPLIED or SHORTLISTED applications can be selected"));
		}

		@Test
		void alreadySelectedIs409() {
			karanNimbus.setStatus(ApplicationStatus.SELECTED);

			assertThatThrownBy(() -> select(12L))
					.isInstanceOfSatisfying(ConflictException.class, ex -> assertThat(ex.getDetails())
							.containsExactly("Application 12 is already SELECTED"));
		}

		@Test
		void allOrNothingOneBadApplicationAndNobodyIsSelected() {
			karanNimbus.setStatus(ApplicationStatus.REJECTED);
			karanNimbus.setStatusReason("Not cleared in Round 1: Aptitude");

			assertThatThrownBy(() -> select(11L, 12L))
					.isInstanceOfSatisfying(ConflictException.class, ex -> {
						assertThat(ex.getMessage()).isEqualTo(
								"Selection was not saved: 1 problem found, see details. Nothing was changed");
						assertThat(ex.getDetails()).containsExactly("Application 12 is REJECTED (Not cleared in "
								+ "Round 1: Aptitude); only APPLIED or SHORTLISTED applications can be selected");
					});
			assertThat(riyaNimbus.getStatus()).isEqualTo(ApplicationStatus.SHORTLISTED);
			verify(auditService, never()).record(any(), anyLong(), any(), any(), any());
		}

		@Test
		void duplicateOrForeignIdsAre400ListingEveryProblem() {
			assertThatThrownBy(() -> select(11L, 11L, 99L))
					.isInstanceOfSatisfying(BadRequestException.class, ex -> assertThat(ex.getDetails())
							.containsExactly("Application 11 is listed more than once in this batch",
									"Application 99 was not found in this drive"));
			assertThat(riyaNimbus.getStatus()).isEqualTo(ApplicationStatus.SHORTLISTED);
		}

		@Test
		void locksTheDriveThenTheStudentsBeforeLoadingApplicationsAndOffers() {
			when(policyRepository.findByActiveTrue()).thenReturn(List.of(oneOffer(true)));

			select(11L);

			InOrder order = inOrder(driveRepository, studentProfileRepository, applicationRepository);
			order.verify(driveRepository).findByIdForUpdate(DRIVE_ID);
			order.verify(studentProfileRepository).findAllByIdForUpdate(List.of(1L, 2L));
			order.verify(applicationRepository).findByIdInAndDriveId(anyCollection(), eq(DRIVE_ID));
			order.verify(applicationRepository).findSelectedOffers(1L);
		}

	}

	// =====================================================================
	// final round
	// =====================================================================

	@Nested
	class FinalRound {

		private DriveRound round1;
		private DriveRound round2;

		@BeforeEach
		void setUp() {
			round1 = round(101L, nimbus, 1, "Aptitude");
			round2 = round(102L, nimbus, 2, "Technical");
			when(roundRepository.findMaxRoundNo(DRIVE_ID)).thenReturn((short) 2);
			when(roundRepository.findByDriveIdAndRoundNo(DRIVE_ID, (short) 2)).thenReturn(Optional.of(round2));
		}

		@Test
		void clearedTheFinalRoundCanBeSelected() {
			when(resultRepository.findWithRoundByApplicationIdIn(anyCollection())).thenReturn(List.of(
					result(round1, riyaNimbus, RoundOutcome.CLEARED),
					result(round2, riyaNimbus, RoundOutcome.CLEARED)));

			select(11L);

			assertThat(riyaNimbus.getStatus()).isEqualTo(ApplicationStatus.SELECTED);
		}

		@Test
		void clearedOnlyAnEarlierRoundIs409NamingTheFinalRound() {
			when(resultRepository.findWithRoundByApplicationIdIn(anyCollection())).thenReturn(List.of(
					result(round1, riyaNimbus, RoundOutcome.CLEARED)));

			assertThatThrownBy(() -> select(11L))
					.isInstanceOfSatisfying(ConflictException.class, ex -> assertThat(ex.getDetails())
							.containsExactly("Application 11 has not cleared the final round (Round 2: Technical)"));
		}

		@Test
		void appliedWithoutAnyResultIs409WhenTheDriveHasRounds() {
			when(resultRepository.findWithRoundByApplicationIdIn(anyCollection())).thenReturn(List.of());

			assertThatThrownBy(() -> select(12L))
					.isInstanceOfSatisfying(ConflictException.class, ex -> assertThat(ex.getDetails())
							.containsExactly("Application 12 has not cleared the final round (Round 2: Technical)"));
		}

	}

	@Test
	void driveWithoutRoundsSelectsDirectlyFromApplied() {
		when(roundRepository.findMaxRoundNo(DRIVE_ID)).thenReturn(null);

		select(12L);

		assertThat(karanNimbus.getStatus()).isEqualTo(ApplicationStatus.SELECTED);
		verify(resultRepository, never()).findWithRoundByApplicationIdIn(any());
	}

	// =====================================================================
	// decision #45: policies at selection time and re-evaluation afterwards
	// =====================================================================

	@Nested
	class PolicyReEvaluation {

		@Test
		void oneOfferMakesEveryOtherPendingFullTimeApplicationIneligible() {
			when(policyRepository.findByActiveTrue()).thenReturn(List.of(oneOffer(true)));

			SelectionResponse response = select(11L);

			String reason = "One-offer policy: you already hold an offer of 12.00 LPA from Nimbus Systems; "
					+ "students with an offer cannot apply to more full-time drives";
			assertThat(riyaOrbit.getStatus()).isEqualTo(ApplicationStatus.INELIGIBLE);
			assertThat(riyaOrbit.getStatusReason()).isEqualTo(reason);
			assertThat(riyaZenith.getStatus()).isEqualTo(ApplicationStatus.INELIGIBLE);
			assertThat(response.madeIneligible()).extracting(SelectionResponse.MadeIneligible::applicationId)
					.containsExactly(21L, 22L);
			assertThat(response.madeIneligible().get(0).companyName()).isEqualTo("Orbit Labs");
			assertThat(auditedNewValue(21L)).containsEntry("status", "INELIGIBLE").containsEntry("reason", reason)
					.containsEntry("trigger", "POLICY");
		}

		@Test
		void dreamOfferFirstOfferBelowThresholdKeepsDreamApplicationsActive() {
			when(policyRepository.findByActiveTrue()).thenReturn(List.of(dreamOffer(true, "10")));
			nimbus.setCtcLpa(new BigDecimal("6.00")); // below the 10 LPA threshold

			SelectionResponse response = select(11L);

			// Orbit (8 LPA) is not an upgrade: blocked. Zenith (15 LPA) is a dream drive: stays active.
			assertThat(riyaOrbit.getStatus()).isEqualTo(ApplicationStatus.INELIGIBLE);
			assertThat(riyaOrbit.getStatusReason()).isEqualTo("Dream-offer policy: you hold an offer of 6.00 LPA "
					+ "from Nimbus Systems; you may only apply to drives of 10.00 LPA or more (this drive offers "
					+ "8.00 LPA)");
			assertThat(riyaZenith.getStatus()).isEqualTo(ApplicationStatus.SHORTLISTED);
			assertThat(riyaZenith.getStatusReason()).isNull();
			assertThat(response.madeIneligible()).extracting(SelectionResponse.MadeIneligible::applicationId)
					.containsExactly(21L);
		}

		@Test
		void dreamOfferADreamOfferBlocksEverythingElse() {
			when(policyRepository.findByActiveTrue()).thenReturn(List.of(dreamOffer(true, "10")));
			// Nimbus is 12 LPA, at or above the threshold

			select(11L);

			assertThat(riyaOrbit.getStatus()).isEqualTo(ApplicationStatus.INELIGIBLE);
			assertThat(riyaZenith.getStatus()).isEqualTo(ApplicationStatus.INELIGIBLE);
			assertThat(riyaZenith.getStatusReason()).startsWith("Dream-offer policy: you already hold a dream offer "
					+ "of 12.00 LPA from Nimbus Systems");
		}

		@Test
		void dreamOfferTheUpgradeUsesTheOneAllowedUpgrade() {
			when(policyRepository.findByActiveTrue()).thenReturn(List.of(dreamOffer(true, "10")));
			when(applicationRepository.findSelectedOffers(1L)).thenReturn(List.of(fullTimeOffer("Acme", "6")));

			select(11L); // 12 LPA >= 10: the upgrade is allowed

			assertThat(riyaNimbus.getStatus()).isEqualTo(ApplicationStatus.SELECTED);
			assertThat(riyaZenith.getStatus()).isEqualTo(ApplicationStatus.INELIGIBLE);
			assertThat(riyaZenith.getStatusReason()).contains("the one allowed upgrade has been used");
		}

		@Test
		void maxOffersChangesNothingBelowTheLimit() {
			when(policyRepository.findByActiveTrue()).thenReturn(List.of(maxOffers(true, 2)));

			SelectionResponse response = select(11L); // 1 offer of 2

			assertThat(riyaOrbit.getStatus()).isEqualTo(ApplicationStatus.APPLIED);
			assertThat(riyaZenith.getStatus()).isEqualTo(ApplicationStatus.SHORTLISTED);
			assertThat(response.madeIneligible()).isEmpty();
		}

		@Test
		void maxOffersReachingTheLimitMakesPendingApplicationsIneligible() {
			when(policyRepository.findByActiveTrue()).thenReturn(List.of(maxOffers(true, 2)));
			when(applicationRepository.findSelectedOffers(1L)).thenReturn(List.of(fullTimeOffer("Acme", "6")));

			select(11L); // now 2 offers of 2

			assertThat(riyaOrbit.getStatus()).isEqualTo(ApplicationStatus.INELIGIBLE);
			assertThat(riyaOrbit.getStatusReason()).isEqualTo("Max-offers policy: you already hold 2 offers "
					+ "(Acme 6.00 LPA, Nimbus Systems 12.00 LPA); the limit is 2 offers");
			assertThat(riyaZenith.getStatus()).isEqualTo(ApplicationStatus.INELIGIBLE);
		}

		@Test
		void aPolicyThatNowBlocksTheApplicationItselfIs409() {
			// e.g. ONE_OFFER was switched on after Riya applied and got an offer elsewhere
			when(policyRepository.findByActiveTrue()).thenReturn(List.of(oneOffer(true)));
			when(applicationRepository.findSelectedOffers(1L)).thenReturn(List.of(fullTimeOffer("Acme", "6")));

			assertThatThrownBy(() -> select(11L))
					.isInstanceOfSatisfying(ConflictException.class, ex -> assertThat(ex.getDetails()).containsExactly(
							"Application 11 is blocked by a placement policy: One-offer policy: you already hold an "
									+ "offer of 6.00 LPA from Acme; students with an offer cannot apply to more "
									+ "full-time drives"));
			assertThat(riyaNimbus.getStatus()).isEqualTo(ApplicationStatus.SHORTLISTED);
			assertThat(riyaOrbit.getStatus()).isEqualTo(ApplicationStatus.APPLIED);
		}

		@Test
		void internshipSelectionIsNotAnOfferSoNothingIsReEvaluated() {
			nimbus.setJobType(JobType.INTERNSHIP);
			lenient().when(policyRepository.findByActiveTrue()).thenReturn(List.of(oneOffer(true)));

			select(11L);

			assertThat(riyaNimbus.getStatus()).isEqualTo(ApplicationStatus.SELECTED);
			assertThat(riyaOrbit.getStatus()).isEqualTo(ApplicationStatus.APPLIED);
			verify(policyRepository, never()).findByActiveTrue();
			verify(applicationRepository, never()).findPendingFullTimeByStudentId(anyLong(), anyLong());
		}

	}

	// =====================================================================
	// completing a drive
	// =====================================================================

	@Nested
	class RejectPending {

		@Test
		void everyPendingApplicationIsRejectedAndAudited() {
			when(applicationRepository.findStudentIdsByDriveIdAndStatusIn(DRIVE_ID, SelectionService.SELECTABLE))
					.thenReturn(List.of(1L, 2L));
			when(applicationRepository.findByDriveIdAndStatusIn(DRIVE_ID, SelectionService.SELECTABLE))
					.thenReturn(List.of(riyaNimbus, karanNimbus));

			int rejected = service.rejectPending(nimbus);

			assertThat(rejected).isEqualTo(2);
			assertThat(riyaNimbus.getStatus()).isEqualTo(ApplicationStatus.REJECTED);
			assertThat(riyaNimbus.getStatusReason()).isEqualTo("Drive completed without selection");
			assertThat(karanNimbus.getStatus()).isEqualTo(ApplicationStatus.REJECTED);
			assertThat(auditedNewValue(12L)).containsEntry("status", "REJECTED")
					.containsEntry("trigger", "DRIVE_COMPLETED");

			InOrder order = inOrder(studentProfileRepository, applicationRepository);
			order.verify(studentProfileRepository).findAllByIdForUpdate(List.of(1L, 2L));
			order.verify(applicationRepository).findByDriveIdAndStatusIn(DRIVE_ID, SelectionService.SELECTABLE);
		}

		@Test
		void nothingPendingLocksNothing() {
			when(applicationRepository.findStudentIdsByDriveIdAndStatusIn(DRIVE_ID, SelectionService.SELECTABLE))
					.thenReturn(List.of());

			assertThat(service.rejectPending(nimbus)).isZero();
			verify(studentProfileRepository, never()).findAllByIdForUpdate(any());
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
