package com.placementhub.api;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import com.placementhub.common.Branch;
import com.placementhub.company.Company;
import com.placementhub.drive.Drive;
import com.placementhub.drive.DriveStatus;
import com.placementhub.drive.JobType;
import com.placementhub.support.ApiTest;
import com.placementhub.support.ApiTestBase;
import com.placementhub.user.User;

@ApiTest
class DriveApiTest extends ApiTestBase {

	private static final String DRIVES = "/api/drives";

	private User admin;
	private Company company;

	@BeforeEach
	void setUp() {
		admin = fixtures.admin();
		company = fixtures.company("Api Co");
	}

	private String createBody(long companyId, String driveDate) {
		return """
				{"companyId":%d,"roleTitle":"Engineer","jobType":"FULL_TIME","ctcLpa":8.5,"minCgpa":7.0,"maxBacklogs":0,
				 "graduationYear":2027,"registrationDeadline":"2099-06-15T10:00:00Z","driveDate":%s,"branches":["CSE"]}
				""".formatted(companyId, driveDate == null ? "null" : "\"" + driveDate + "\"");
	}

	@Test
	void create201StartsAsDraftWithALocation() throws Exception {
		String companyName = company.getName();

		mvc.perform(as(admin, post(DRIVES).contentType(MediaType.APPLICATION_JSON)
				.content(createBody(company.getId(), "2099-06-20"))))
				.andExpect(status().isCreated())
				.andExpect(header().string("Location", containsString("/api/drives/")))
				.andExpect(jsonPath("$.status").value("DRAFT"))
				.andExpect(jsonPath("$.companyName").value(companyName));
	}

	@Test
	void createWithInvalidFields400ListsThem() throws Exception {
		mvc.perform(as(admin, post(DRIVES).contentType(MediaType.APPLICATION_JSON).content("{\"roleTitle\":\"\"}")))
				.andExpectAll(errorJson(400, DRIVES))
				.andExpect(jsonPath("$.details").isArray());
	}

	/** The gap from decision 78: this is invalid input, so 400, not 409. */
	@Test
	void createWithADriveDateBeforeTheDeadline400() throws Exception {
		mvc.perform(as(admin, post(DRIVES).contentType(MediaType.APPLICATION_JSON)
				.content(createBody(company.getId(), "2099-06-01"))))
				.andExpectAll(errorJson(400, DRIVES))
				.andExpect(jsonPath("$.message").value("Drive date cannot be before the registration deadline"));
	}

	@Test
	void updateWithADriveDateBeforeTheDeadline400() throws Exception {
		Drive drive = fixtures.drive(company, admin, JobType.FULL_TIME, "8.00", DriveStatus.DRAFT);
		String body = """
				{"roleTitle":"Engineer","jobType":"FULL_TIME","ctcLpa":8.5,"minCgpa":7.0,"maxBacklogs":0,
				 "graduationYear":2027,"registrationDeadline":"2099-06-15T10:00:00Z","driveDate":"2099-06-01",
				 "branches":["CSE"]}
				""";

		mvc.perform(as(admin, put(DRIVES + "/" + drive.getId()).contentType(MediaType.APPLICATION_JSON).content(body)))
				.andExpectAll(errorJson(400, DRIVES + "/" + drive.getId()));
	}

	@Test
	void createForAMissingCompany404() throws Exception {
		mvc.perform(as(admin, post(DRIVES).contentType(MediaType.APPLICATION_JSON)
				.content(createBody(999_999_999L, null))))
				.andExpectAll(errorJson(404, DRIVES));
	}

	@Test
	void getAMissingDrive404() throws Exception {
		mvc.perform(as(admin, get(DRIVES + "/999999999"))).andExpectAll(errorJson(404, DRIVES + "/999999999"));
	}

	@Test
	void anIdThatIsNotANumber400() throws Exception {
		mvc.perform(as(admin, get(DRIVES + "/abc"))).andExpectAll(errorJson(400, DRIVES + "/abc"));
	}

	@Test
	void openingAnOpenDrive409() throws Exception {
		Drive drive = fixtures.drive(company, admin, JobType.FULL_TIME, "8.00", DriveStatus.OPEN);
		String path = DRIVES + "/" + drive.getId() + "/open";

		mvc.perform(as(admin, post(path))).andExpectAll(errorJson(409, path));
	}

	@Test
	void openThenClose200() throws Exception {
		Drive drive = fixtures.drive(company, admin, JobType.FULL_TIME, "8.00", DriveStatus.DRAFT);

		mvc.perform(as(admin, post(DRIVES + "/" + drive.getId() + "/open")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("OPEN"));
		mvc.perform(as(admin, post(DRIVES + "/" + drive.getId() + "/close")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("CLOSED"));
	}

	@Test
	void noTokenCreate401() throws Exception {
		mvc.perform(post(DRIVES).contentType(MediaType.APPLICATION_JSON).content(createBody(company.getId(), null)))
				.andExpectAll(errorJson(401, DRIVES));
	}

	@Test
	void aStudentCreating403() throws Exception {
		User student = fixtures.student("Stu", Branch.CSE, "8.00").getUser();

		mvc.perform(as(student, post(DRIVES).contentType(MediaType.APPLICATION_JSON)
				.content(createBody(company.getId(), null))))
				.andExpectAll(errorJson(403, DRIVES));
	}

}
