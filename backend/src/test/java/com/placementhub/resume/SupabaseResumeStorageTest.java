package com.placementhub.resume;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.placementhub.common.ExternalServiceException;

/** The exact requests sent to Supabase and how its answers are handled, against a mock server (no network). */
class SupabaseResumeStorageTest {

	private static final String BASE = "https://proj.supabase.test";
	private static final String KEY = "sb_secret_not-a-real-key";
	private static final String OBJECT = BASE + "/storage/v1/object/resumes/students/7/a.pdf";
	private static final String SIGN = BASE + "/storage/v1/object/sign/resumes/students/7/a.pdf";
	private static final byte[] PDF = "%PDF-1.7".getBytes(StandardCharsets.US_ASCII);

	private MockRestServiceServer server;
	private SupabaseResumeStorage storage;

	@BeforeEach
	void setUp() {
		RestClient.Builder builder = RestClient.builder();
		server = MockRestServiceServer.bindTo(builder).build();
		// A trailing slash on the URL must not produce "//storage"
		storage = new SupabaseResumeStorage(properties(BASE + "/", KEY), builder);
	}

	private static ResumeProperties properties(String url, String key) {
		return new ResumeProperties("supabase", Duration.ofMinutes(5),
				new ResumeProperties.Supabase(url, key, "resumes"));
	}

	@Test
	void uploadPostsTheBytesAsAPdfWithTheKeyInBothHeaders() {
		server.expect(requestTo(OBJECT))
				.andExpect(method(HttpMethod.POST))
				.andExpect(header("apikey", KEY))
				.andExpect(header("Authorization", "Bearer " + KEY))
				.andExpect(content().contentType(MediaType.APPLICATION_PDF))
				.andExpect(content().bytes(PDF))
				.andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

		storage.upload("students/7/a.pdf", PDF);

		server.verify();
	}

	@Test
	void signedUrlAsksForTheTtlAndTurnsTheRelativeAnswerIntoAFullLink() {
		server.expect(requestTo(SIGN))
				.andExpect(method(HttpMethod.POST))
				.andExpect(header("apikey", KEY))
				.andExpect(jsonPath("$.expiresIn").value(300))
				.andRespond(withSuccess("{\"signedURL\":\"/object/sign/resumes/students/7/a.pdf?token=abc\"}",
						MediaType.APPLICATION_JSON));

		String url = storage.signedUrl("students/7/a.pdf", Duration.ofMinutes(5));

		assertThat(url).isEqualTo(BASE + "/storage/v1/object/sign/resumes/students/7/a.pdf?token=abc");
		server.verify();
	}

	@Test
	void deleteSendsDeleteToTheObject() {
		server.expect(requestTo(OBJECT))
				.andExpect(method(HttpMethod.DELETE))
				.andExpect(header("apikey", KEY))
				.andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

		storage.delete("students/7/a.pdf");

		server.verify();
	}

	@Test
	void anErrorAnswerBecomes502WithOurMessageNotSupabases() {
		server.expect(requestTo(OBJECT))
				.andRespond(withStatus(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON)
						.body("{\"message\":\"secret internal detail\"}"));

		assertThatThrownBy(() -> storage.upload("students/7/a.pdf", PDF))
				.isInstanceOfSatisfying(ExternalServiceException.class, ex -> {
					assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY);
					assertThat(ex.getMessage()).isEqualTo(SupabaseResumeStorage.FAILED_MESSAGE);
				});
	}

	@Test
	void aServerErrorAnswerAlsoBecomes502() {
		server.expect(requestTo(OBJECT)).andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

		assertThatThrownBy(() -> storage.delete("students/7/a.pdf"))
				.isInstanceOfSatisfying(ExternalServiceException.class,
						ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY));
	}

	@Test
	void aNetworkFailureBecomes503() {
		server.expect(requestTo(OBJECT)).andRespond(withException(new SocketTimeoutException("timed out")));

		assertThatThrownBy(() -> storage.upload("students/7/a.pdf", PDF))
				.isInstanceOfSatisfying(ExternalServiceException.class, ex -> {
					assertThat(ex.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
					assertThat(ex.getMessage()).isEqualTo(SupabaseResumeStorage.UNAVAILABLE_MESSAGE);
				});
	}

	@Test
	void aSignAnswerWithoutAUrlBecomes502() {
		server.expect(requestTo(SIGN)).andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

		assertThatThrownBy(() -> storage.signedUrl("students/7/a.pdf", Duration.ofMinutes(5)))
				.isInstanceOfSatisfying(ExternalServiceException.class,
						ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY));
	}

	@Test
	void aSignAnswerThatIsNotJsonBecomes502() {
		server.expect(requestTo(SIGN)).andRespond(withSuccess("<html>gateway</html>", MediaType.TEXT_HTML));

		assertThatThrownBy(() -> storage.signedUrl("students/7/a.pdf", Duration.ofMinutes(5)))
				.isInstanceOfSatisfying(ExternalServiceException.class,
						ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY));
	}

	@Test
	void missingSettingsAnswer503WithoutAnyRequestAndTheAppStillStarts() {
		SupabaseResumeStorage unconfigured = new SupabaseResumeStorage(properties("", ""), RestClient.builder());

		assertThatThrownBy(() -> unconfigured.upload("students/7/a.pdf", PDF))
				.isInstanceOfSatisfying(ExternalServiceException.class,
						ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
	}

	@Test
	void theSecretKeyIsNeverInTheSettingsToString() {
		assertThat(new ResumeProperties.Supabase(BASE, KEY, "resumes").toString()).doesNotContain(KEY);
	}

}
