package com.placementhub.policy;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Body for PUT /api/policies/{type}: replaces that policy's settings.
 * Which of the two settings is required (or forbidden) depends on the type; PolicyService checks that.
 */
public record PolicyUpdateRequest(

		@NotNull
		Boolean active,

		/** DREAM_OFFER only. NUMERIC(6,2) in the DB, so at most 4 digits before the point. */
		@DecimalMin(value = "0.00", inclusive = false)
		@Digits(integer = 4, fraction = 2)
		BigDecimal ctcThresholdLpa,

		/** MAX_OFFERS only. Capped so it always fits the SMALLINT column. */
		@Min(1)
		@Max(20)
		Integer maxOffers) {
}
