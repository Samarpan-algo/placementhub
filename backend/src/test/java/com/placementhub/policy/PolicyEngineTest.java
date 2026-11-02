package com.placementhub.policy;

import static com.placementhub.policy.PolicyFixtures.dreamOffer;
import static com.placementhub.policy.PolicyFixtures.drive;
import static com.placementhub.policy.PolicyFixtures.fullTimeOffer;
import static com.placementhub.policy.PolicyFixtures.internshipOffer;
import static com.placementhub.policy.PolicyFixtures.maxOffers;
import static com.placementhub.policy.PolicyFixtures.oneOffer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.placementhub.drive.JobType;

class PolicyEngineTest {

	private final PolicyEngine engine = PolicyFixtures.engine();

	// ---------- which drives and offers count ----------

	@Test
	void noActivePoliciesAllowsEverything() {
		PolicyEvaluation result = engine.evaluate(List.of(), drive(JobType.FULL_TIME, "5.00"),
				List.of(fullTimeOffer("A", "6.00"), fullTimeOffer("B", "7.00")));

		assertThat(result.allowed()).isTrue();
		assertThat(result.reasons()).isEmpty();
	}

	@Test
	void internshipDrivesAreNeverRestrictedByPolicies() {
		PolicyEvaluation result = engine.evaluate(List.of(oneOffer(true), maxOffers(true, 1)),
				drive(JobType.INTERNSHIP, "3.60"), List.of(fullTimeOffer("Kaveri Analytics", "6.50")));

		assertThat(result).isEqualTo(PolicyEvaluation.ALLOWED);
		assertThat(engine.appliesTo(drive(JobType.INTERNSHIP, "3.60"))).isFalse();
		assertThat(engine.appliesTo(drive(JobType.FULL_TIME, "3.60"))).isTrue();
	}

	@Test
	void internshipSelectionsDoNotCountAsOffers() {
		PolicyEvaluation result = engine.evaluate(List.of(oneOffer(true)), drive(JobType.FULL_TIME, "12.00"),
				List.of(internshipOffer("Nimbus Systems", "3.60")));

		assertThat(result.allowed()).isTrue();
	}

	@Test
	void onlyFullTimeOffersAreCountedAndShownInTheMessage() {
		// 1 full-time + 1 internship = 1 offer, below a limit of 2
		PolicyEvaluation allowed = engine.evaluate(List.of(maxOffers(true, 2)), drive(JobType.FULL_TIME, "12.00"),
				List.of(fullTimeOffer("Kaveri Analytics", "6.50"), internshipOffer("Nimbus Systems", "3.60")));
		assertThat(allowed.allowed()).isTrue();

		PolicyEvaluation blocked = engine.evaluate(List.of(oneOffer(true)), drive(JobType.FULL_TIME, "12.00"),
				List.of(fullTimeOffer("Kaveri Analytics", "6.50"), internshipOffer("Nimbus Systems", "3.60")));
		assertThat(blocked.reasons()).singleElement().asString()
				.contains("an offer of 6.50 LPA from Kaveri Analytics")
				.doesNotContain("Nimbus");
	}

	@Test
	void inactivePoliciesAreIgnored() {
		PolicyEvaluation result = engine.evaluate(List.of(oneOffer(false), maxOffers(false, 1)),
				drive(JobType.FULL_TIME, "12.00"), List.of(fullTimeOffer("Kaveri Analytics", "6.50")));

		assertThat(result.allowed()).isTrue();
	}

	// ---------- combining rules ----------

	@Test
	void everyBlockingRuleIsReportedInAFixedOrder() {
		// given out of order on purpose: MAX before DREAM
		PolicyEvaluation result = engine.evaluate(List.of(maxOffers(true, 1), dreamOffer(true, "10.00")),
				drive(JobType.FULL_TIME, "8.00"), List.of(fullTimeOffer("Kaveri Analytics", "6.00")));

		assertThat(result.allowed()).isFalse();
		assertThat(result.reasons()).containsExactly(
				"Dream-offer policy: you hold an offer of 6.00 LPA from Kaveri Analytics; you may only apply to "
						+ "drives of 10.00 LPA or more (this drive offers 8.00 LPA)",
				"Max-offers policy: you already hold an offer of 6.00 LPA from Kaveri Analytics; the limit is 1 offer");
	}

	@Test
	void oneBlockingRuleIsEnoughEvenIfAnotherAllows() {
		// DREAM allows the upgrade (12 >= 10), but MAX_OFFERS = 1 blocks
		PolicyEvaluation result = engine.evaluate(List.of(dreamOffer(true, "10.00"), maxOffers(true, 1)),
				drive(JobType.FULL_TIME, "12.00"), List.of(fullTimeOffer("Kaveri Analytics", "6.00")));

		assertThat(result.allowed()).isFalse();
		assertThat(result.reasons()).singleElement().asString().startsWith("Max-offers policy");
	}

	@Test
	void allActiveRulesAllowingMeansAllowed() {
		PolicyEvaluation result = engine.evaluate(List.of(dreamOffer(true, "10.00"), maxOffers(true, 2)),
				drive(JobType.FULL_TIME, "12.00"), List.of(fullTimeOffer("Kaveri Analytics", "6.00")));

		assertThat(result.allowed()).isTrue();
		assertThat(result.reasons()).isEmpty();
	}

	// ---------- wiring ----------

	@Test
	void missingRuleForATypeFailsAtStartup() {
		assertThatThrownBy(() -> new PolicyEngine(List.of(new OneOfferRule(), new DreamOfferRule())))
				.isInstanceOf(IllegalStateException.class)
				.hasMessage("No PolicyRule bean for MAX_OFFERS");
	}

	@Test
	void twoRulesForTheSameTypeFailAtStartup() {
		assertThatThrownBy(() -> new PolicyEngine(
				List.of(new OneOfferRule(), new OneOfferRule(), new DreamOfferRule(), new MaxOffersRule())))
				.isInstanceOf(IllegalStateException.class)
				.hasMessage("Two PolicyRule beans for ONE_OFFER");
	}

}
