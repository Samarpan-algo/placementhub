package com.placementhub.policy;

import java.math.BigDecimal;
import java.time.Instant;

public record PolicyResponse(
		PolicyType type,
		boolean active,
		BigDecimal ctcThresholdLpa,
		Integer maxOffers,
		Instant updatedAt) {
}
