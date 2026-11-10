package com.placementhub.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import com.placementhub.auth.AuthUser;
import com.placementhub.auth.TokenService;
import com.placementhub.user.Role;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Upload limits are enforced by the real web server before any controller runs, and MockMvc has no web
 * server, so this test starts a real one on a random port and sends real bytes. The request is rejected
 * before the database or the storage is used, so nothing is left behind.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ResumeUploadLimitTest {

	private static final int MB = 1024 * 1024;

	@LocalServerPort
	private int port;
	@Autowired
	private TokenService tokenService;
	@Autowired
	private JsonMapper jsonMapper;

	@ParameterizedTest(name = "{0} MB")
	@ValueSource(ints = { 3, 20 })
	void aFileOverTheLimitGetsA400WithOurErrorJson(int megabytes) throws Exception {
		String token = tokenService.issue(new AuthUser(2L, "student@test.dev", Role.STUDENT, "unused")).value();
		String boundary = "limit-test-boundary";

		HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/students/me/resume"))
				.header("Authorization", "Bearer " + token)
				.header("Content-Type", "multipart/form-data; boundary=" + boundary)
				.POST(HttpRequest.BodyPublishers.ofByteArray(multipartBody(boundary, megabytes * MB)))
				.build();

		try (HttpClient client = HttpClient.newHttpClient()) {
			HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

			assertThat(response.statusCode()).isEqualTo(400);
			JsonNode body = jsonMapper.readTree(response.body());
			assertThat(body.get("status").asInt()).isEqualTo(400);
			assertThat(body.get("message").asString()).isEqualTo("The file is too large. The maximum size is 2 MB");
			assertThat(body.get("path").asString()).isEqualTo("/api/students/me/resume");
			assertThat(body.get("timestamp").isNull()).isFalse();
		}
	}

	private static byte[] multipartBody(String boundary, int fileSize) throws Exception {
		ByteArrayOutputStream body = new ByteArrayOutputStream(fileSize + 500);
		body.write(("--" + boundary + "\r\n"
				+ "Content-Disposition: form-data; name=\"file\"; filename=\"big.pdf\"\r\n"
				+ "Content-Type: application/pdf\r\n\r\n%PDF-").getBytes(StandardCharsets.US_ASCII));
		body.write(new byte[fileSize]);
		body.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.US_ASCII));
		return body.toByteArray();
	}

}
