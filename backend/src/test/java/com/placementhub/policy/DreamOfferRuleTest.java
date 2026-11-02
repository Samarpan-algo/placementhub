package com.placementhub.policy;

import static com.placementhub.policy.PolicyFixtures.drive;
import static com.placementhub.policy.PolicyFixtures.fullTimeOffer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.placementhub.drive.JobType;

/** Threshold T = 10.00 LPA in every test. */
class DreamOfferRuleTest {

	private final DreamOfferRule rule = new DreamOfferRule();
	private final PolicySettings tenLpa = new PolicySettings(new BigDecimal("10.00"), null);

	@Test
	void configuresTheDreamOfferRow() {
		assertThat(rule.type()).isEqualTo(PolicyType.DREAM_OFFER);
	}

	// ---------- 0 offers ----------

	@Test
	void noOffersIsAllowedEvenBelowTheThreshold() {
		assertThat(rule.evaluate(tenLpa, drive(JobType.FULL_TIME, "4.50"), List.of()).allowed()).isTrue();
	}

	// ---------- 1 offer below T ----------

	@Test
	void oneOfferBelowThresholdMayUpgradeToADriveAboveIt() {
		PolicyResult result = rule.evaluate(tenLpa, drive(JobType.FULL_TIME, "12.00"),
				List.of(fullTimeOffer("Kaveri Analytics", "6.00")));

		assertThat(result.allowed()).isTrue();
	}

	@Test
	void driveCtcExactlyEqualToThresholdCountsAsAnUpgrade() {
		// 10 vs 10.00: compareTo, not equals
		PolicyResult result = rule.evaluate(tenLpa, drive(JobType.FULL_TIME, "10"),
				List.of(fullTimeOffer("Kaveri Analytics", "6.00")));

		assertThat(result.allowed()).isTrue();
	}

	@Test
	void oneOfferBelowThresholdCannotApplyToAnotherDriveBelowIt() {
		PolicyResult result = rule.evaluate(tenLpa, drive(JobType.FULL_TIME, "8.00"),
				List.of(fullTimeOffer("Kaveri Analytics", "6.00")));

		assertThat(result.allowed()).isFalse();
		assertThat(result.reason()).isEqualTo("Dream-offer policy: you hold an offer of 6.00 LPA from Kaveri "
				+ "Analytics; you may only apply to drives of 10.00 LPA or more (this drive offers 8.00 LPA)");
	}

	@Test
	void driveJustBelowThresholdIsBlocked() {
		PolicyResult result = rule.evaluate(tenLpa, drive(JobType.FULL_TIME, "9.99"),
				List.of(fullTimeOffer("Kaveri Analytics", "6.00")));

		assertThat(result.allowed()).isFalse();
	}

	// ---------- 1 offer at or above T ----------

	@Test
	void offerExactlyEqualToThresholdIsADreamOfferSoNoMoreApplications() {
		PolicyResult result = rule.evaluate(tenLpa, drive(JobType.FULL_TIME, "15.00"),
				List.of(fullTimeOffer("Nimbus Systems", "10")));

		assertThat(result.allowed()).isFalse();
		assertThat(result.reason()).isEqualTo("Dream-offer policy: you already hold a dream offer of 10.00 LPA "
				+ "from Nimbus Systems (10.00 LPA or more); no further full-time applications are allowed");
	}

	@Test
	void offerAboveThresholdBlocksEvenHigherDrives() {
		PolicyResult result = rule.evaluate(tenLpa, drive(JobType.FULL_TIME, "20.00"),
				List.of(fullTimeOffer("Nimbus Systems", "12.00")));

		assertThat(result.allowed()).isFalse();
		assertThat(result.reason()).startsWith("Dream-offer policy: you already hold a dream offer of 12.00 LPA");
	}

	// ---------- 2+ offers ----------

	@Test
	void twoOffersAreBlockedEvenForADriveAboveThreshold() {
		PolicyResult result = rule.evaluate(tenLpa, drive(JobType.FULL_TIME, "15.00"),
				List.of(fullTimeOffer("Kaveri Analytics", "6.00"), fullTimeOffer("Ironbridge Engineering", "4.50")));

		assertThat(result.allowed()).isFalse();
		assertThat(result.reason()).isEqualTo("Dream-offer policy: you already hold 2 offers (Kaveri Analytics "
				+ "6.00 LPA, Ironbridge Engineering 4.50 LPA); the one allowed upgrade has been used, so no "
				+ "further full-time applications are allowed");
	}

	@Test
	void threeOffersAreBlocked() {
		PolicyResult result = rule.evaluate(tenLpa, drive(JobType.FULL_TIME, "15.00"),
				List.of(fullTimeOffer("A", "6.00"), fullTimeOffer("B", "7.00"), fullTimeOffer("C", "12.00")));

		assertThat(result.allowed()).isFalse();
		assertThat(result.reason()).contains("3 offers (A 6.00 LPA, B 7.00 LPA, C 12.00 LPA)");
	}

	// ---------- misconfiguration ----------

	@Test
	void missingThresholdFailsLoudly() {
		assertThatThrownBy(() -> rule.evaluate(new PolicySettings(null, null), drive(JobType.FULL_TIME, "12.00"),
				List.of()))
				.isInstanceOf(IllegalStateException.class)
				.hasMessage("DREAM_OFFER policy has no CTC threshold");
	}

}
