package com.placementhub.policy;

import java.math.BigDecimal;
import java.util.List;

import com.placementhub.company.Company;
import com.placementhub.drive.Drive;
import com.placementhub.drive.JobType;
import com.placementhub.user.Role;
import com.placementhub.user.User;

/** Test data for policy tests, shared with the application and student service tests. */
public final class PolicyFixtures {

	private PolicyFixtures() {
	}

	/** The real engine with the real rules, as Spring would build it. */
	public static PolicyEngine engine() {
		return new PolicyEngine(List.of(new OneOfferRule(), new DreamOfferRule(), new MaxOffersRule()));
	}

	public static PlacementPolicy oneOffer(boolean active) {
		PlacementPolicy policy = new PlacementPolicy(PolicyType.ONE_OFFER);
		policy.setActive(active);
		return policy;
	}

	public static PlacementPolicy dreamOffer(boolean active, String thresholdLpa) {
		PlacementPolicy policy = new PlacementPolicy(PolicyType.DREAM_OFFER);
		policy.setActive(active);
		policy.setCtcThresholdLpa(new BigDecimal(thresholdLpa));
		return policy;
	}

	public static PlacementPolicy maxOffers(boolean active, int limit) {
		PlacementPolicy policy = new PlacementPolicy(PolicyType.MAX_OFFERS);
		policy.setActive(active);
		policy.setMaxOffers((short) limit);
		return policy;
	}

	public static Offer fullTimeOffer(String company, String ctcLpa) {
		return new Offer(company, new BigDecimal(ctcLpa), JobType.FULL_TIME);
	}

	public static Offer internshipOffer(String company, String ctcLpa) {
		return new Offer(company, new BigDecimal(ctcLpa), JobType.INTERNSHIP);
	}

	/** A drive with only the fields policies look at. */
	public static Drive drive(JobType jobType, String ctcLpa) {
		Drive drive = new Drive(new Company("Nimbus Systems", null, null),
				new User("admin@placementhub.dev", "hash", Role.TPO_ADMIN));
		drive.setJobType(jobType);
		drive.setCtcLpa(new BigDecimal(ctcLpa));
		return drive;
	}

}
