package com.placementhub.policy;

import java.math.BigDecimal;

/** A rule's settings, copied from its placement_policies row. Unused fields are null. */
public record PolicySettings(BigDecimal ctcThresholdLpa, Integer maxOffers) {

	public static PolicySettings from(PlacementPolicy policy) {
		Short max = policy.getMaxOffers();
		return new PolicySettings(policy.getCtcThresholdLpa(), max == null ? null : max.intValue());
	}

}
