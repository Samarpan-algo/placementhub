package com.placementhub.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.placementhub.common.Branch;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

/** Bean Validation rules on RegisterRequest, checked with a real validator (no Spring needed). */
class RegisterRequestValidationTest {

	private static ValidatorFactory factory;
	private static Validator validator;

	@BeforeAll
	static void setUpValidator() {
		factory = Validation.buildDefaultValidatorFactory();
		validator = factory.getValidator();
	}

	@AfterAll
	static void closeValidator() {
		factory.close();
	}

	@ParameterizedTest(name = "valid roll number: \"{0}\"")
	@ValueSource(strings = { "23CSE099", "12345", "ABCDE", "ABCDEFGHIJ0123456789", " 23cse099 " })
	void acceptsFiveToTwentyLettersOrDigits(String rollNo) {
		assertThat(rollNoErrors(rollNo)).isEmpty();
	}

	@ParameterizedTest(name = "invalid roll number: \"{0}\"")
	@ValueSource(strings = { "2", "23CS", "ABCDEFGHIJ01234567890", "23-CSE-099", "23 CSE 099", "23CSE_99", "23CSÉ99" })
	void rejectsTooShortTooLongOrNonAlphanumeric(String rollNo) {
		assertThat(rollNoErrors(rollNo)).containsExactly("must be 5 to 20 letters or digits, e.g. 23CSE099");
	}

	@Test
	void lowercaseAndSpacesAreNormalisedBeforeValidation() {
		RegisterRequest request = request(" 23cse099 ");

		assertThat(request.rollNo()).isEqualTo("23CSE099");
		assertThat(validator.validate(request)).isEmpty();
	}

	@Test
	void blankRollNoIsRejected() {
		assertThat(rollNoErrors("   ")).contains("must not be blank");
	}

	private List<String> rollNoErrors(String rollNo) {
		return validator.validate(request(rollNo)).stream()
				.filter(violation -> violation.getPropertyPath().toString().equals("rollNo"))
				.map(ConstraintViolation::getMessage)
				.toList();
	}

	private static RegisterRequest request(String rollNo) {
		return new RegisterRequest("neha@student.placementhub.dev", "Secret@123", "Neha Kapoor", rollNo, Branch.CSE,
				2027, new BigDecimal("8.50"), 0, null);
	}

}
