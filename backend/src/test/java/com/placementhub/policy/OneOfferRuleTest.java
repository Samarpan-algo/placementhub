package com.placementhub.policy;

import static com.placementhub.policy.PolicyFixtures.drive;
import static com.placementhub.policy.PolicyFixtures.fullTimeOffer;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.placementhub.drive.JobType;

class OneOfferRuleTest {

	private final OneOfferRule rule = new OneOfferRule();
	private final PolicySettings noSettings = new PolicySettings(null, null);

	@Test
	void configuresTheOneOfferRow() {
		assertThat(rule.type()).isEqualTo(PolicyType.ONE_OFFER);
	}

	@Test
	void noOffersIsAllowed() {
		PolicyResult result = rule.evaluate(noSettings, drive(JobType.FULL_TIME, "12.00"), List.of());

		assertThat(result.allowed()).isTrue();
		assertThat(result.reason()).isNull();
	}

	@Test
	void oneOfferIsBlockedWithCompanyAndCtc() {
		PolicyResult result = rule.evaluate(noSettings, drive(JobType.FULL_TIME, "12.00"),
				List.of(fullTimeOffer("Kaveri Analytics", "6.5")));

		assertThat(result.allowed()).isFalse();
		assertThat(result.reason()).isEqualTo("One-offer policy: you already hold an offer of 6.50 LPA from "
				+ "Kaveri Analytics; students with an offer cannot apply to more full-time drives");
	}

	@Test
	void twoOffersAreBlockedAndBothAreListed() {
		PolicyResult result = rule.evaluate(noSettings, drive(JobType.FULL_TIME, "12.00"),
				List.of(fullTimeOffer("Kaveri Analytics", "6.50"), fullTimeOffer("Nimbus Systems", "12.00")));

		assertThat(result.allowed()).isFalse();
		assertThat(result.reason()).isEqualTo("One-offer policy: you already hold 2 offers (Kaveri Analytics "
				+ "6.50 LPA, Nimbus Systems 12.00 LPA); students with an offer cannot apply to more full-time drives");
	}

}
