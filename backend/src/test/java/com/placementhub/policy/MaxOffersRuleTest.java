package com.placementhub.policy;

import static com.placementhub.policy.PolicyFixtures.drive;
import static com.placementhub.policy.PolicyFixtures.fullTimeOffer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.placementhub.drive.Drive;
import com.placementhub.drive.JobType;

class MaxOffersRuleTest {

	private final MaxOffersRule rule = new MaxOffersRule();
	private final PolicySettings limitTwo = new PolicySettings(null, 2);
	private final Drive drive = drive(JobType.FULL_TIME, "12.00");

	@Test
	void configuresTheMaxOffersRow() {
		assertThat(rule.type()).isEqualTo(PolicyType.MAX_OFFERS);
	}

	@Test
	void noOffersIsAllowed() {
		assertThat(rule.evaluate(limitTwo, drive, List.of()).allowed()).isTrue();
	}

	@Test
	void oneOfferBelowALimitOfTwoIsAllowed() {
		assertThat(rule.evaluate(limitTwo, drive, List.of(fullTimeOffer("Kaveri Analytics", "6.50"))).allowed())
				.isTrue();
	}

	@Test
	void offersExactlyEqualToTheLimitAreBlocked() {
		PolicyResult result = rule.evaluate(limitTwo, drive,
				List.of(fullTimeOffer("Kaveri Analytics", "6.50"), fullTimeOffer("Nimbus Systems", "12.00")));

		assertThat(result.allowed()).isFalse();
		assertThat(result.reason()).isEqualTo("Max-offers policy: you already hold 2 offers (Kaveri Analytics "
				+ "6.50 LPA, Nimbus Systems 12.00 LPA); the limit is 2 offers");
	}

	@Test
	void offersAboveTheLimitAreBlocked() {
		PolicyResult result = rule.evaluate(limitTwo, drive,
				List.of(fullTimeOffer("A", "4.00"), fullTimeOffer("B", "5.00"), fullTimeOffer("C", "6.00")));

		assertThat(result.allowed()).isFalse();
		assertThat(result.reason()).contains("3 offers").endsWith("the limit is 2 offers");
	}

	@Test
	void limitOfOneUsesSingularWording() {
		PolicyResult result = rule.evaluate(new PolicySettings(null, 1), drive,
				List.of(fullTimeOffer("Kaveri Analytics", "6.50")));

		assertThat(result.allowed()).isFalse();
		assertThat(result.reason()).isEqualTo("Max-offers policy: you already hold an offer of 6.50 LPA from "
				+ "Kaveri Analytics; the limit is 1 offer");
	}

	@Test
	void missingLimitFailsLoudly() {
		assertThatThrownBy(() -> rule.evaluate(new PolicySettings(null, null), drive, List.of()))
				.isInstanceOf(IllegalStateException.class)
				.hasMessage("MAX_OFFERS policy has no offer limit");
	}

}
