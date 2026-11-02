package com.placementhub.policy;

import java.util.List;

import org.springframework.stereotype.Component;

import com.placementhub.drive.Drive;

/** ONE_OFFER: once a student holds any offer, they cannot apply to more full-time drives. */
@Component
public class OneOfferRule implements PolicyRule {

	@Override
	public PolicyType type() {
		return PolicyType.ONE_OFFER;
	}

	@Override
	public PolicyResult evaluate(PolicySettings settings, Drive drive, List<Offer> offers) {
		if (offers.isEmpty()) {
			return PolicyResult.allow();
		}
		return PolicyResult.block("One-offer policy: you already hold " + OfferText.describe(offers)
				+ "; students with an offer cannot apply to more full-time drives");
	}

}
