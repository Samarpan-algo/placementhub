package com.placementhub.api;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import com.placementhub.application.Application;
import com.placementhub.application.ApplicationStatus;
import com.placementhub.common.Branch;
import com.placementhub.company.Company;
import com.placementhub.drive.Drive;
import com.placementhub.drive.DriveStatus;
import com.placementhub.drive.JobType;
import com.placementhub.student.StudentProfile;
import com.placementhub.support.ApiTest;
import com.placementhub.support.ApiTestBase;
import com.placementhub.user.User;

@ApiTest
class ApplyApiTest extends ApiTestBase {

	private static final String APPLICATIONS = "/api/students/me/applications";

	private User admin;
	private Company company;
	private Drive openDrive;
	private StudentProfile student;

	@BeforeEach
	void setUp() {
		admin = fixtures.admin();
		company = fixtures.company("Apply Co");
		openDrive = fixtures.drive(company, admin, JobType.FULL_TIME, "8.00", DriveStatus.OPEN); // min CGPA 6, CSE/IT
		student = fixtures.student("Riya", Branch.CSE, "8.00");
	}

	private static String applyBody(Long driveId) {
		return "{\"driveId\":" + driveId + "}";
	}

	@Test
	void apply201WithALocation() throws Exception {
		mvc.perform(as(student.getUser(), post(APPLICATIONS).contentType(MediaType.APPLICATION_JSON)
				.content(applyBody(openDrive.getId()))))
				.andExpect(status().isCreated())
				.andExpect(header().string("Location", containsString(APPLICATIONS + "/")))
				.andExpect(jsonPath("$.status").value("APPLIED"));
	}

	@Test
	void applyWithoutADriveId400() throws Exception {
		mvc.perform(as(student.getUser(), post(APPLICATIONS).contentType(MediaType.APPLICATION_JSON).content("{}")))
				.andExpectAll(errorJson(400, APPLICATIONS));
	}

	@Test
	void applyWithoutAToken401() throws Exception {
		mvc.perform(post(APPLICATIONS).contentType(MediaType.APPLICATION_JSON).content(applyBody(openDrive.getId())))
				.andExpectAll(errorJson(401, APPLICATIONS));
	}

	@Test
	void anAdminApplying403() throws Exception {
		mvc.perform(as(admin, post(APPLICATIONS).contentType(MediaType.APPLICATION_JSON)
				.content(applyBody(openDrive.getId()))))
				.andExpectAll(errorJson(403, APPLICATIONS));
	}

	@Test
	void applyToAMissingDrive404() throws Exception {
		mvc.perform(as(student.getUser(), post(APPLICATIONS).contentType(MediaType.APPLICATION_JSON)
				.content(applyBody(999_999_999L))))
				.andExpectAll(errorJson(404, APPLICATIONS));
	}

	@Test
	void applyToADraftDriveLooksMissing404() throws Exception {
		Drive draft = fixtures.drive(company, admin, JobType.FULL_TIME, "8.00", DriveStatus.DRAFT);

		mvc.perform(as(student.getUser(), post(APPLICATIONS).contentType(MediaType.APPLICATION_JSON)
				.content(applyBody(draft.getId()))))
				.andExpectAll(errorJson(404, APPLICATIONS));
	}

	@Test
	void applyingTwice409() throws Exception {
		fixtures.application(openDrive, student, ApplicationStatus.APPLIED);

		mvc.perform(as(student.getUser(), post(APPLICATIONS).contentType(MediaType.APPLICATION_JSON)
				.content(applyBody(openDrive.getId()))))
				.andExpectAll(errorJson(409, APPLICATIONS));
	}

	@Test
	void notEligible422ListsEveryReason() throws Exception {
		StudentProfile lowCgpaOtherBranch = fixtures.student("Low", Branch.ME, "5.00");

		mvc.perform(as(lowCgpaOtherBranch.getUser(), post(APPLICATIONS).contentType(MediaType.APPLICATION_JSON)
				.content(applyBody(openDrive.getId()))))
				.andExpectAll(errorJson(422, APPLICATIONS))
				.andExpect(jsonPath("$.details.length()").value(2));
	}

	@Test
	void withdraw200ThenAgain409() throws Exception {
		Application application = fixtures.application(openDrive, student, ApplicationStatus.APPLIED);
		String path = APPLICATIONS + "/" + application.getId() + "/withdraw";

		mvc.perform(as(student.getUser(), post(path)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("WITHDRAWN"));
		mvc.perform(as(student.getUser(), post(path))).andExpectAll(errorJson(409, path));
	}

	/** 404, not 403: a student can't even learn that another student's application exists. */
	@Test
	void anotherStudentsApplicationLooksMissing404() throws Exception {
		Application application = fixtures.application(openDrive, student, ApplicationStatus.APPLIED);
		User other = fixtures.student("Other", Branch.CSE, "8.00").getUser();
		String one = APPLICATIONS + "/" + application.getId();

		mvc.perform(as(other, get(one))).andExpectAll(errorJson(404, one));
		mvc.perform(as(other, post(one + "/withdraw"))).andExpectAll(errorJson(404, one + "/withdraw"));
	}

}
