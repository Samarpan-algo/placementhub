package com.placementhub.support;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;

import com.placementhub.auth.AuthUser;
import com.placementhub.auth.TokenService;
import com.placementhub.user.User;

import jakarta.persistence.EntityManager;

/** What every {@link ApiTest} needs: MockMvc, fixtures, real tokens and the check of the error JSON. */
public abstract class ApiTestBase {

	@Autowired
	protected MockMvc mvc;
	@Autowired
	protected EntityManager em;
	@Autowired
	private TokenService tokenService;

	protected DbFixtures fixtures;

	@BeforeEach
	void createFixtures() {
		fixtures = new DbFixtures(em);
	}

	/** Adds a real signed token for this user. Fixtures are flushed first, so requests can see them. */
	protected <B extends AbstractMockHttpServletRequestBuilder<B>> B as(User user, B request) {
		fixtures.flushAndClear();
		String token = tokenService.issue(new AuthUser(user.getId(), user.getEmail(), user.getRole(), "unused"))
				.value();
		return request.header("Authorization", "Bearer " + token);
	}

	/** The response has this status and our standard error JSON: {timestamp, status, error, message, path}. */
	protected static ResultMatcher[] errorJson(int statusCode, String path) {
		return new ResultMatcher[] {
				status().is(statusCode),
				jsonPath("$.timestamp").isNotEmpty(),
				jsonPath("$.status").value(statusCode),
				jsonPath("$.error").isNotEmpty(),
				jsonPath("$.message").isNotEmpty(),
				jsonPath("$.path").value(path),
				jsonPath("$.trace").doesNotExist() };
	}

}
