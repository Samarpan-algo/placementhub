package com.placementhub.policy;

import java.util.List;

import org.springframework.stereotype.Component;

import com.placementhub.drive.Drive;

/** MAX_OFFERS (limit N): a student holding N offers cannot apply to more full-time drives. */
@Component
public class MaxOffersRule implements PolicyRule {

	@Override
	public PolicyType type() {
		return PolicyType.MAX_OFFERS;
	}

	@Override
	public PolicyResult evaluate(PolicySettings settings, Drive drive, List<Offer> offers) {
		Integer limit = settings.maxOffers();
		if (limit == null) {
			// the DB CHECK forbids this; fail loudly rather than guess a limit
			throw new IllegalStateException("MAX_OFFERS policy has no offer limit");
		}
		if (offers.size() < limit) {
			return PolicyResult.allow();
		}
		return PolicyResult.block("Max-offers policy: you already hold " + OfferText.describe(offers)
				+ "; the limit is " + limit + (limit == 1 ? " offer" : " offers"));
	}

}
