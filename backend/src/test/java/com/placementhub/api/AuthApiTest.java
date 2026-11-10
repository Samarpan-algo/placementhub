package com.placementhub.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import com.placementhub.support.ApiTest;
import com.placementhub.support.ApiTestBase;

@ApiTest
class AuthApiTest extends ApiTestBase {

	private static final String REGISTER = "/api/auth/register";
	private static final String LOGIN = "/api/auth/login";

	private static String registerBody(String email, String rollNo, String password) {
		return """
				{"email":"%s","password":"%s","fullName":"New Student","rollNo":"%s","branch":"CSE",
				 "graduationYear":2027,"cgpa":8.1,"activeBacklogs":0}
				""".formatted(email, password, rollNo);
	}

	private static String loginBody(String email, String password) {
		return "{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password);
	}

	@Test
	void register201ThenTheNewStudentCanLogIn() throws Exception {
		mvc.perform(post(REGISTER).contentType(MediaType.APPLICATION_JSON)
				.content(registerBody("api.new@test.dev", "APINEW01", "Password1")))
				.andExpect(status().isCreated())
				.andExpect(header().string("Location", "/api/students/me"))
				.andExpect(jsonPath("$.email").value("api.new@test.dev"))
				.andExpect(jsonPath("$.hasResume").value(false))
				.andExpect(jsonPath("$.passwordHash").doesNotExist());

		mvc.perform(post(LOGIN).contentType(MediaType.APPLICATION_JSON)
				.content(loginBody("api.new@test.dev", "Password1")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.tokenType").value("Bearer"))
				.andExpect(jsonPath("$.role").value("STUDENT"))
				.andExpect(jsonPath("$.accessToken").isNotEmpty());
	}

	@Test
	void registerWithInvalidFields400ListsEachOne() throws Exception {
		mvc.perform(post(REGISTER).contentType(MediaType.APPLICATION_JSON)
				.content(registerBody("not-an-email", "APINEW02", "short")))
				.andExpectAll(errorJson(400, REGISTER))
				.andExpect(jsonPath("$.details.length()").value(2));
	}

	@Test
	void registerWithATakenEmail409() throws Exception {
		mvc.perform(post(REGISTER).contentType(MediaType.APPLICATION_JSON)
				.content(registerBody("admin@placementhub.dev", "APINEW03", "Password1")))
				.andExpectAll(errorJson(409, REGISTER))
				.andExpect(jsonPath("$.message").value("An account with this email already exists"));
	}

	@Test
	void malformedJson400() throws Exception {
		mvc.perform(post(LOGIN).contentType(MediaType.APPLICATION_JSON).content("{not json"))
				.andExpectAll(errorJson(400, LOGIN));
	}

	@Test
	void wrongPasswordAndUnknownEmailGetTheSame401() throws Exception {
		mvc.perform(post(LOGIN).contentType(MediaType.APPLICATION_JSON)
				.content(loginBody("admin@placementhub.dev", "wrong-password")))
				.andExpectAll(errorJson(401, LOGIN))
				.andExpect(jsonPath("$.message").value("Invalid email or password"));

		mvc.perform(post(LOGIN).contentType(MediaType.APPLICATION_JSON)
				.content(loginBody("nobody@test.dev", "wrong-password")))
				.andExpectAll(errorJson(401, LOGIN))
				.andExpect(jsonPath("$.message").value("Invalid email or password"));
	}

	@Test
	void unknownUrlWithAValidTokenIsAJson404NotAStackTrace() throws Exception {
		mvc.perform(as(fixtures.admin(), get("/api/no-such-endpoint")))
				.andExpectAll(errorJson(404, "/api/no-such-endpoint"));
	}

}
