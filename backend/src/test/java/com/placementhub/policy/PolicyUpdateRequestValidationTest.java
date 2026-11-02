package com.placementhub.policy;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

/** The Bean Validation annotations on the request (what @Valid turns into a 400). */
class PolicyUpdateRequestValidationTest {

	private static ValidatorFactory factory;
	private static Validator validator;

	@BeforeAll
	static void createValidator() {
		factory = Validation.buildDefaultValidatorFactory();
		validator = factory.getValidator();
	}

	@AfterAll
	static void closeValidator() {
		factory.close();
	}

	private static Set<String> invalidFields(PolicyUpdateRequest request) {
		return validator.validate(request).stream()
				.map(v -> v.getPropertyPath().toString())
				.collect(Collectors.toSet());
	}

	@Test
	void validRequestsPass() {
		assertThat(invalidFields(new PolicyUpdateRequest(true, new BigDecimal("10.00"), null))).isEmpty();
		assertThat(invalidFields(new PolicyUpdateRequest(false, null, 1))).isEmpty();
		assertThat(invalidFields(new PolicyUpdateRequest(true, new BigDecimal("0.01"), 20))).isEmpty();
	}

	@Test
	void activeIsRequired() {
		assertThat(invalidFields(new PolicyUpdateRequest(null, null, null))).containsExactly("active");
	}

	@ParameterizedTest
	@ValueSource(strings = { "0", "0.00", "-5", "10.555", "10000" })
	void thresholdMustBePositiveWithAtMostTwoDecimalsAndFitTheColumn(String threshold) {
		assertThat(invalidFields(new PolicyUpdateRequest(true, new BigDecimal(threshold), null)))
				.containsExactly("ctcThresholdLpa");
	}

	@ParameterizedTest
	@ValueSource(ints = { 0, -1, 21 })
	void maxOffersMustBeBetween1And20(int maxOffers) {
		assertThat(invalidFields(new PolicyUpdateRequest(true, null, maxOffers))).containsExactly("maxOffers");
	}

}
