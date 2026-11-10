package com.placementhub.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import com.placementhub.round.DriveRound;
import com.placementhub.student.StudentProfile;
import com.placementhub.support.ApiTest;
import com.placementhub.support.ApiTestBase;
import com.placementhub.user.User;

@ApiTest
class ResultsAndSelectionApiTest extends ApiTestBase {

	private User admin;
	private Company company;
	private Drive closedDrive;
	private DriveRound round;
	private StudentProfile student;
	private Application application;

	@BeforeEach
	void setUp() {
		admin = fixtures.admin();
		company = fixtures.company("Results Co");
		closedDrive = fixtures.drive(company, admin, JobType.INTERNSHIP, "8.00", DriveStatus.CLOSED);
		round = fixtures.round(closedDrive, 1, "Technical");
		student = fixtures.student("Riya", Branch.CSE, "8.00");
		application = fixtures.application(closedDrive, student, ApplicationStatus.APPLIED);
	}

	private String resultsPath() {
		return "/api/drives/" + closedDrive.getId() + "/rounds/" + round.getId() + "/results";
	}

	private String selectionsPath() {
		return "/api/drives/" + closedDrive.getId() + "/selections";
	}

	private static String sheet(Long applicationId, String result) {
		return "{\"results\":[{\"applicationId\":" + applicationId + ",\"result\":\"" + result + "\"}]}";
	}

	private static String selection(Long applicationId) {
		return "{\"applicationIds\":[" + applicationId + "]}";
	}

	// ---------- results ----------

	@Test
	void saveResults200UpdatesTheStatus() throws Exception {
		mvc.perform(as(admin, put(resultsPath()).contentType(MediaType.APPLICATION_JSON)
				.content(sheet(application.getId(), "CLEARED"))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.results[0].applicationStatus").value("SHORTLISTED"));
	}

	@Test
	void anEmptySheet400() throws Exception {
		mvc.perform(as(admin, put(resultsPath()).contentType(MediaType.APPLICATION_JSON).content("{\"results\":[]}")))
				.andExpectAll(errorJson(400, resultsPath()));
	}

	@Test
	void anApplicationFromAnotherDrive400WithDetails() throws Exception {
		mvc.perform(as(admin, put(resultsPath()).contentType(MediaType.APPLICATION_JSON)
				.content(sheet(999_999_999L, "CLEARED"))))
				.andExpectAll(errorJson(400, resultsPath()))
				.andExpect(jsonPath("$.details[0]").isNotEmpty());
	}

	@Test
	void resultsForAMissingRound404() throws Exception {
		String path = "/api/drives/" + closedDrive.getId() + "/rounds/999999999/results";

		mvc.perform(as(admin, put(path).contentType(MediaType.APPLICATION_JSON)
				.content(sheet(application.getId(), "CLEARED"))))
				.andExpectAll(errorJson(404, path));
	}

	@Test
	void resultsWhileTheDriveIsOpen409() throws Exception {
		Drive open = fixtures.drive(company, admin, JobType.INTERNSHIP, "8.00", DriveStatus.OPEN);
		DriveRound openRound = fixtures.round(open, 1, "Technical");
		Application app = fixtures.application(open, student, ApplicationStatus.APPLIED);
		String path = "/api/drives/" + open.getId() + "/rounds/" + openRound.getId() + "/results";

		mvc.perform(as(admin, put(path).contentType(MediaType.APPLICATION_JSON).content(sheet(app.getId(), "CLEARED"))))
				.andExpectAll(errorJson(409, path));
	}

	@Test
	void resultsWithoutAToken401AndAsAStudent403() throws Exception {
		mvc.perform(put(resultsPath()).contentType(MediaType.APPLICATION_JSON)
				.content(sheet(application.getId(), "CLEARED")))
				.andExpectAll(errorJson(401, resultsPath()));

		mvc.perform(as(student.getUser(), put(resultsPath()).contentType(MediaType.APPLICATION_JSON)
				.content(sheet(application.getId(), "CLEARED"))))
				.andExpectAll(errorJson(403, resultsPath()));
	}

	// ---------- selection ----------

	@Test
	void selectAfterClearingTheFinalRound200() throws Exception {
		mvc.perform(as(admin, put(resultsPath()).contentType(MediaType.APPLICATION_JSON)
				.content(sheet(application.getId(), "CLEARED"))))
				.andExpect(status().isOk());

		mvc.perform(as(admin, post(selectionsPath()).contentType(MediaType.APPLICATION_JSON)
				.content(selection(application.getId()))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.selected[0].status").value("SELECTED"));
	}

	@Test
	void selectWithoutClearingTheFinalRound409() throws Exception {
		mvc.perform(as(admin, post(selectionsPath()).contentType(MediaType.APPLICATION_JSON)
				.content(selection(application.getId()))))
				.andExpectAll(errorJson(409, selectionsPath()))
				.andExpect(jsonPath("$.details[0]").isNotEmpty());
	}

	@Test
	void selectNobody400() throws Exception {
		mvc.perform(as(admin, post(selectionsPath()).contentType(MediaType.APPLICATION_JSON)
				.content("{\"applicationIds\":[]}")))
				.andExpectAll(errorJson(400, selectionsPath()));
	}

	@Test
	void selectInAMissingDrive404() throws Exception {
		String path = "/api/drives/999999999/selections";

		mvc.perform(as(admin, post(path).contentType(MediaType.APPLICATION_JSON)
				.content(selection(application.getId()))))
				.andExpectAll(errorJson(404, path));
	}

	@Test
	void selectWithoutAToken401AndAsAStudent403() throws Exception {
		mvc.perform(post(selectionsPath()).contentType(MediaType.APPLICATION_JSON)
				.content(selection(application.getId())))
				.andExpectAll(errorJson(401, selectionsPath()));

		mvc.perform(as(student.getUser(), post(selectionsPath()).contentType(MediaType.APPLICATION_JSON)
				.content(selection(application.getId()))))
				.andExpectAll(errorJson(403, selectionsPath()));
	}

}
