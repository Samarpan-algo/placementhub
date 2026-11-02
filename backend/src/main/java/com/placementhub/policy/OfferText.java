package com.placementhub.policy;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.stream.Collectors;

/** Shared wording for policy messages, so every rule describes offers the same way. */
final class OfferText {

	private OfferText() {
	}

	/**
	 * "an offer of 6.50 LPA from Kaveri Analytics", or
	 * "2 offers (Kaveri Analytics 6.50 LPA, Nimbus Systems 12.00 LPA)".
	 */
	static String describe(List<Offer> offers) {
		if (offers.size() == 1) {
			Offer offer = offers.get(0);
			return "an offer of " + lpa(offer.ctcLpa()) + " from " + offer.companyName();
		}
		return offers.size() + " offers (" + offers.stream()
				.map(o -> o.companyName() + " " + lpa(o.ctcLpa()))
				.collect(Collectors.joining(", ")) + ")";
	}

	/** "6.50 LPA" */
	static String lpa(BigDecimal value) {
		return value.setScale(2, RoundingMode.HALF_UP).toPlainString() + " LPA";
	}

}
