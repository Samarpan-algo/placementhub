package com.placementhub.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

import com.placementhub.application.ApplicationStatus;
import com.placementhub.common.Branch;
import com.placementhub.common.ExternalServiceException;
import com.placementhub.drive.Drive;
import com.placementhub.drive.DriveStatus;
import com.placementhub.drive.JobType;
import com.placementhub.resume.FakeResumeStorage;
import com.placementhub.student.StudentProfile;
import com.placementhub.support.ApiTest;
import com.placementhub.support.ApiTestBase;
import com.placementhub.user.User;

@ApiTest
class ResumeApiTest extends ApiTestBase {

	private static final String MINE = "/api/students/me/resume";
	private static final byte[] PDF = "%PDF-1.7 a resume".getBytes(StandardCharsets.US_ASCII);

	@Autowired
	private FakeResumeStorage storage;

	private StudentProfile student;
	private User admin;

	@BeforeEach
	void setUp() {
		storage.reset();
		student = fixtures.student("Riya", Branch.CSE, "8.00");
		admin = fixtures.admin();
	}

	@AfterEach
	void clearStorage() {
		storage.reset();
	}

	private static MockMultipartFile file(String contentType, byte[] bytes) {
		return new MockMultipartFile("file", "my cv.pdf", contentType, bytes);
	}

	private static MockMultipartHttpServletRequestBuilder upload(MockMultipartFile file) {
		return multipart(MINE).file(file);
	}

	private String adminPath() {
		return "/api/students/" + student.getId() + "/resume";
	}

	private void uploadAs(StudentProfile who) throws Exception {
		mvc.perform(as(who.getUser(), upload(file("application/pdf", PDF)))).andExpect(status().isOk());
	}

	// ---------- upload ----------

	@Test
	void upload200StoresUnderTheStudentAndNeverShowsThePath() throws Exception {
		mvc.perform(as(student.getUser(), upload(file("application/pdf", PDF))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.hasResume").value(true))
				.andExpect(jsonPath("$.resumePath").doesNotExist())
				.andExpect(content().string(not(containsString("students/"))));

		assertThat(storage.storedPaths()).singleElement().asString()
				.matches("students/" + student.getId() + "/[0-9a-f-]{36}\\.pdf");
	}

	@Test
	void aSecondUploadReplacesTheFileAndDeletesTheOldObject() throws Exception {
		uploadAs(student);
		Set<String> first = storage.storedPaths();

		uploadAs(student);

		assertThat(storage.storedPaths()).hasSize(1).doesNotContainAnyElementsOf(first);
	}

	@Test
	void aWrongContentType400() throws Exception {
		mvc.perform(as(student.getUser(), upload(file("image/png", PDF))))
				.andExpectAll(errorJson(400, MINE))
				.andExpect(jsonPath("$.message").value("Only PDF files are allowed"));
		assertThat(storage.storedPaths()).isEmpty();
	}

	@Test
	void pdfContentTypeButNotPdfBytes400() throws Exception {
		mvc.perform(as(student.getUser(), upload(file("application/pdf", "<html>".getBytes(StandardCharsets.US_ASCII)))))
				.andExpectAll(errorJson(400, MINE))
				.andExpect(jsonPath("$.message").value("This file is not a valid PDF"));
		assertThat(storage.storedPaths()).isEmpty();
	}

	@Test
	void anEmptyFile400() throws Exception {
		mvc.perform(as(student.getUser(), upload(file("application/pdf", new byte[0]))))
				.andExpectAll(errorJson(400, MINE));
	}

	@Test
	void aPartWithTheWrongName400() throws Exception {
		MockMultipartFile wrongName = new MockMultipartFile("resume", "cv.pdf", "application/pdf", PDF);

		mvc.perform(as(student.getUser(), multipart(MINE).file(wrongName)))
				.andExpectAll(errorJson(400, MINE));
	}

	@Test
	void aRequestThatIsNotMultipart400() throws Exception {
		mvc.perform(as(student.getUser(), post(MINE).contentType(MediaType.APPLICATION_JSON).content("{}")))
				.andExpectAll(errorJson(400, MINE));
	}

	@Test
	void uploadWithoutAToken401() throws Exception {
		mvc.perform(upload(file("application/pdf", PDF))).andExpectAll(errorJson(401, MINE));
	}

	@Test
	void anAdminUploading403() throws Exception {
		mvc.perform(as(admin, upload(file("application/pdf", PDF)))).andExpectAll(errorJson(403, MINE));
		assertThat(storage.storedPaths()).isEmpty();
	}

	// ---------- viewing ----------

	@Test
	void myResumeBeforeAnyUpload404() throws Exception {
		mvc.perform(as(student.getUser(), get(MINE)))
				.andExpectAll(errorJson(404, MINE))
				.andExpect(jsonPath("$.message").value("You have not uploaded a resume yet"));
	}

	@Test
	void myResumeGivesAFiveMinuteLink() throws Exception {
		uploadAs(student);

		mvc.perform(as(student.getUser(), get(MINE)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.url").value(containsString("students/" + student.getId() + "/")))
				.andExpect(jsonPath("$.expiresInSeconds").value(300));
	}

	@Test
	void myResumeWithoutAToken401() throws Exception {
		mvc.perform(get(MINE)).andExpectAll(errorJson(401, MINE));
	}

	@Test
	void anAdminCanViewAnyStudentsResume() throws Exception {
		uploadAs(student);

		mvc.perform(as(admin, get(adminPath())))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.url").value(containsString("students/" + student.getId() + "/")));
	}

	/** Ownership: the id in the URL is admin-only, and "me" comes from the token. */
	@Test
	void aStudentCannotViewAnotherStudentsResume403() throws Exception {
		uploadAs(student);
		User other = fixtures.student("Other", Branch.CSE, "8.00").getUser();

		mvc.perform(as(other, get(adminPath()))).andExpectAll(errorJson(403, adminPath()));
		mvc.perform(as(student.getUser(), get(adminPath()))).andExpectAll(errorJson(403, adminPath()));
	}

	@Test
	void anAdminOnTheStudentOnlyRoute403() throws Exception {
		mvc.perform(as(admin, get(MINE))).andExpectAll(errorJson(403, MINE));
	}

	@Test
	void adminViewNoResume404AndNoStudent404() throws Exception {
		mvc.perform(as(admin, get(adminPath())))
				.andExpectAll(errorJson(404, adminPath()))
				.andExpect(jsonPath("$.message").value("This student has not uploaded a resume"));

		mvc.perform(as(admin, get("/api/students/999999999/resume")))
				.andExpectAll(errorJson(404, "/api/students/999999999/resume"));
	}

	@Test
	void adminViewWithAnIdThatIsNotANumber400() throws Exception {
		mvc.perform(as(admin, get("/api/students/abc/resume")))
				.andExpectAll(errorJson(400, "/api/students/abc/resume"));
	}

	@Test
	void theApplicantsListSaysWhoHasAResume() throws Exception {
		Drive drive = fixtures.drive(fixtures.company("Resume Co"), admin, JobType.FULL_TIME, "8.00", DriveStatus.OPEN);
		fixtures.application(drive, student, ApplicationStatus.APPLIED);

		mvc.perform(as(admin, get("/api/drives/" + drive.getId() + "/applications")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content[0].hasResume").value(false));

		uploadAs(student);

		mvc.perform(as(admin, get("/api/drives/" + drive.getId() + "/applications")))
				.andExpect(jsonPath("$.content[0].hasResume").value(true))
				.andExpect(content().string(not(containsString("students/"))));
	}

	// ---------- storage failures ----------

	@Test
	void uploadWhileStorageIsDown503AndTheOldResumeStays() throws Exception {
		uploadAs(student);
		storage.failWith(ExternalServiceException.unavailable("The resume storage service is unavailable."));

		mvc.perform(as(student.getUser(), upload(file("application/pdf", PDF))))
				.andExpectAll(errorJson(503, MINE))
				.andExpect(jsonPath("$.message").value("The resume storage service is unavailable."));

		storage.failWith(null);
		mvc.perform(as(student.getUser(), get(MINE))).andExpect(status().isOk());
	}

	@Test
	void viewingWhenStorageReturnsAnError502() throws Exception {
		uploadAs(student);
		storage.failWith(ExternalServiceException.badGateway("The resume storage service returned an error."));

		mvc.perform(as(student.getUser(), get(MINE))).andExpectAll(errorJson(502, MINE));
		mvc.perform(as(admin, get(adminPath()))).andExpectAll(errorJson(502, adminPath()));
	}

}
