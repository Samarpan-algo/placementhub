package com.placementhub.policy;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.stereotype.Component;

import com.placementhub.drive.Drive;

/**
 * DREAM_OFFER (threshold T): a student with one offer below T gets one upgrade, to a drive with CTC >= T.
 * Allowed only with 0 offers, or with 1 offer below T when the new drive is at or above T.
 */
@Component
public class DreamOfferRule implements PolicyRule {

	@Override
	public PolicyType type() {
		return PolicyType.DREAM_OFFER;
	}

	@Override
	public PolicyResult evaluate(PolicySettings settings, Drive drive, List<Offer> offers) {
		BigDecimal threshold = settings.ctcThresholdLpa();
		if (threshold == null) {
			// the DB CHECK forbids this; fail loudly rather than guess a threshold
			throw new IllegalStateException("DREAM_OFFER policy has no CTC threshold");
		}

		// 1. No offer yet: anything goes
		if (offers.isEmpty()) {
			return PolicyResult.allow();
		}

		// 2. Two or more offers: the one upgrade has been used
		if (offers.size() >= 2) {
			return PolicyResult.block("Dream-offer policy: you already hold " + OfferText.describe(offers)
					+ "; the one allowed upgrade has been used, so no further full-time applications are allowed");
		}

		// 3. Exactly one offer, already a dream offer (compareTo, so 10 and 10.00 are equal)
		Offer offer = offers.get(0);
		if (offer.ctcLpa().compareTo(threshold) >= 0) {
			return PolicyResult.block("Dream-offer policy: you already hold a dream offer of "
					+ OfferText.lpa(offer.ctcLpa()) + " from " + offer.companyName() + " ("
					+ OfferText.lpa(threshold) + " or more); no further full-time applications are allowed");
		}

		// 4. Exactly one offer below T: only a drive at or above T is an upgrade
		if (drive.getCtcLpa().compareTo(threshold) >= 0) {
			return PolicyResult.allow();
		}
		return PolicyResult.block("Dream-offer policy: you hold " + OfferText.describe(offers)
				+ "; you may only apply to drives of " + OfferText.lpa(threshold) + " or more (this drive offers "
				+ OfferText.lpa(drive.getCtcLpa()) + ")");
	}

}
