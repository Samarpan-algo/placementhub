package com.placementhub.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import com.placementhub.common.Branch;
import com.placementhub.support.ApiTest;
import com.placementhub.support.ApiTestBase;
import com.placementhub.user.User;

@ApiTest
class PolicyApiTest extends ApiTestBase {

	private static final String POLICIES = "/api/policies";
	private static final String DREAM = POLICIES + "/DREAM_OFFER";
	private static final String ONE = POLICIES + "/ONE_OFFER";

	private User admin;

	@BeforeEach
	void setUp() {
		admin = fixtures.admin();
	}

	private void putPolicy(String path, String body) throws Exception {
		mvc.perform(as(admin, put(path).contentType(MediaType.APPLICATION_JSON).content(body)))
				.andExpect(status().isOk());
	}

	@Test
	void listAndUpdate200() throws Exception {
		mvc.perform(as(admin, get(POLICIES)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(3));

		mvc.perform(as(admin, put(DREAM).contentType(MediaType.APPLICATION_JSON)
				.content("{\"active\":false,\"ctcThresholdLpa\":12.5}")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.ctcThresholdLpa").value(12.5))
				.andExpect(jsonPath("$.active").value(false));
	}

	@Test
	void aSettingThatDoesNotApply400() throws Exception {
		mvc.perform(as(admin, put(ONE).contentType(MediaType.APPLICATION_JSON)
				.content("{\"active\":false,\"maxOffers\":2}")))
				.andExpectAll(errorJson(400, ONE));
	}

	@Test
	void missingActive400() throws Exception {
		mvc.perform(as(admin, put(ONE).contentType(MediaType.APPLICATION_JSON).content("{}")))
				.andExpectAll(errorJson(400, ONE))
				.andExpect(jsonPath("$.details[0]").value("active: must not be null"));
	}

	@Test
	void anUnknownPolicyType400() throws Exception {
		mvc.perform(as(admin, put(POLICIES + "/NOPE").contentType(MediaType.APPLICATION_JSON)
				.content("{\"active\":true}")))
				.andExpectAll(errorJson(400, POLICIES + "/NOPE"));
	}

	@Test
	void activatingContradictoryPolicies409() throws Exception {
		putPolicy(ONE, "{\"active\":false}");
		putPolicy(DREAM, "{\"active\":true,\"ctcThresholdLpa\":10}");

		mvc.perform(as(admin, put(ONE).contentType(MediaType.APPLICATION_JSON).content("{\"active\":true}")))
				.andExpectAll(errorJson(409, ONE));
	}

	@Test
	void noToken401() throws Exception {
		mvc.perform(get(POLICIES)).andExpectAll(errorJson(401, POLICIES));
	}

	@Test
	void aStudent403() throws Exception {
		User student = fixtures.student("Stu", Branch.CSE, "8.00").getUser();

		mvc.perform(as(student, put(ONE).contentType(MediaType.APPLICATION_JSON).content("{\"active\":false}")))
				.andExpectAll(errorJson(403, ONE));
	}

}
