package com.placementhub.policy;

import org.springframework.stereotype.Component;

@Component
public class PolicyMapper {

	public PolicyResponse toResponse(PlacementPolicy policy) {
		Short max = policy.getMaxOffers();
		return new PolicyResponse(policy.getType(), policy.isActive(), policy.getCtcThresholdLpa(),
				max == null ? null : max.intValue(), policy.getUpdatedAt());
	}

}
