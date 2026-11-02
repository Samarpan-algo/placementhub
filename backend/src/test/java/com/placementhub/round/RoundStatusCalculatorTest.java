package com.placementhub.round;

import static com.placementhub.round.RoundFixtures.application;
import static com.placementhub.round.RoundFixtures.drive;
import static com.placementhub.round.RoundFixtures.result;
import static com.placementhub.round.RoundFixtures.round;
import static com.placementhub.round.RoundFixtures.student;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import com.placementhub.application.Application;
import com.placementhub.application.ApplicationStatus;
import com.placementhub.drive.Drive;
import com.placementhub.drive.DriveStatus;
import com.placementhub.drive.JobType;
import com.placementhub.round.RoundStatusCalculator.StatusDecision;

class RoundStatusCalculatorTest {

	private final Drive drive = drive(7L, "Nimbus Systems", JobType.FULL_TIME, "12", DriveStatus.CLOSED);
	private final Application application = application(11L, drive, student(1L, "Riya"), ApplicationStatus.APPLIED);
	private final DriveRound round1 = round(101L, drive, 1, "Aptitude");
	private final DriveRound round2 = round(102L, drive, 2, "Technical");

	@Test
	void noResultsMeansApplied() {
		assertThat(RoundStatusCalculator.statusFrom(List.of()))
				.isEqualTo(new StatusDecision(ApplicationStatus.APPLIED, null));
	}

	@Test
	void allClearedMeansShortlistedWithoutReason() {
		StatusDecision decision = RoundStatusCalculator.statusFrom(List.of(
				result(round1, application, RoundOutcome.CLEARED),
				result(round2, application, RoundOutcome.CLEARED)));

		assertThat(decision).isEqualTo(new StatusDecision(ApplicationStatus.SHORTLISTED, null));
	}

	@ParameterizedTest
	@EnumSource(value = RoundOutcome.class, names = { "NOT_CLEARED", "ABSENT" })
	void notClearedOrAbsentMeansRejectedNamingTheRound(RoundOutcome outcome) {
		StatusDecision decision = RoundStatusCalculator.statusFrom(List.of(
				result(round1, application, RoundOutcome.CLEARED),
				result(round2, application, outcome)));

		assertThat(decision)
				.isEqualTo(new StatusDecision(ApplicationStatus.REJECTED, "Not cleared in Round 2: Technical"));
	}

	@Test
	void earliestFailedRoundIsTheReasonWhateverTheListOrder() {
		StatusDecision decision = RoundStatusCalculator.statusFrom(List.of(
				result(round2, application, RoundOutcome.ABSENT),
				result(round1, application, RoundOutcome.NOT_CLEARED)));

		assertThat(decision.reason()).isEqualTo("Not cleared in Round 1: Aptitude");
	}

}
