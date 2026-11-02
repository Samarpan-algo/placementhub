package com.placementhub.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Connection;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.stream.Stream;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.placementhub.application.ApplicantService;
import com.placementhub.application.ApplicationService;
import com.placementhub.application.SelectionService;
import com.placementhub.audit.AuditQueryService;
import com.placementhub.company.CompanyService;
import com.placementhub.config.CorsConfig;
import com.placementhub.config.JwtConfig;
import com.placementhub.config.JwtProperties;
import com.placementhub.config.SecurityConfig;
import com.placementhub.drive.DriveService;
import com.placementhub.policy.PolicyService;
import com.placementhub.round.RoundResultService;
import com.placementhub.round.RoundService;
import com.placementhub.stats.StatsService;
import com.placementhub.student.StudentDriveService;
import com.placementhub.student.StudentService;
import com.placementhub.user.Role;

/**
 * The access matrix, end to end through the real SecurityConfig and real HS256 tokens.
 * Services are mocked (no database): we only care whether security lets the request through.
 */
@WebMvcTest
@Import({ SecurityConfig.class, JwtConfig.class, CorsConfig.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class,
		SecurityErrorWriter.class })
class SecurityAccessTest {

	@Autowired
	private MockMvc mvc;
	@Autowired
	private JwtEncoder jwtEncoder;
	@Autowired
	private JwtProperties jwtProperties;

	@MockitoBean
	private CompanyService companyService;
	@MockitoBean
	private DriveService driveService;
	@MockitoBean
	private StudentService studentService;
	@MockitoBean
	private StudentDriveService studentDriveService;
	@MockitoBean
	private ApplicationService applicationService;
	@MockitoBean
	private ApplicantService applicantService;
	@MockitoBean
	private PolicyService policyService;
	@MockitoBean
	private RoundService roundService;
	@MockitoBean
	private StatsService statsService;
	@MockitoBean
	private SelectionService selectionService;
	@MockitoBean
	private AuditQueryService auditQueryService;
	@MockitoBean
	private RoundResultService roundResultService;
	@MockitoBean
	private AuthService authService;
	@MockitoBean
	private UserDetailsService userDetailsService;
	@MockitoBean
	private DataSource dataSource;

	private String studentToken;
	private String adminToken;

	@BeforeEach
	void setUp() throws Exception {
		studentToken = tokenFor(Role.STUDENT, Clock.systemUTC());
		adminToken = tokenFor(Role.TPO_ADMIN, Clock.systemUTC());
		Connection connection = mock(Connection.class);
		when(connection.isValid(anyInt())).thenReturn(true);
		when(dataSource.getConnection()).thenReturn(connection);
	}

	// ---------------------------------------------------------------------
	// The matrix: every protected endpoint group, every kind of caller
	// ---------------------------------------------------------------------

	record Endpoint(HttpMethod method, String path, Role requiredRole) {
		@Override
		public String toString() {
			return method + " " + path + " (" + requiredRole + ")";
		}
	}

	static Stream<Endpoint> protectedEndpoints() {
		return Stream.of(
				new Endpoint(HttpMethod.GET, "/api/companies", Role.TPO_ADMIN),
				new Endpoint(HttpMethod.GET, "/api/companies/1", Role.TPO_ADMIN),
				new Endpoint(HttpMethod.DELETE, "/api/companies/1", Role.TPO_ADMIN),
				new Endpoint(HttpMethod.GET, "/api/drives", Role.TPO_ADMIN),
				new Endpoint(HttpMethod.GET, "/api/drives/1", Role.TPO_ADMIN),
				new Endpoint(HttpMethod.POST, "/api/drives/1/open", Role.TPO_ADMIN),
				new Endpoint(HttpMethod.GET, "/api/drives/1/rounds", Role.TPO_ADMIN),
				new Endpoint(HttpMethod.GET, "/api/drives/1/applications", Role.TPO_ADMIN),
				new Endpoint(HttpMethod.GET, "/api/drives/1/applications/export", Role.TPO_ADMIN),
				new Endpoint(HttpMethod.GET, "/api/policies", Role.TPO_ADMIN),
				new Endpoint(HttpMethod.GET, "/api/audit", Role.TPO_ADMIN),
				new Endpoint(HttpMethod.GET, "/api/stats", Role.TPO_ADMIN),
				new Endpoint(HttpMethod.PUT, "/api/policies/DREAM_OFFER", Role.TPO_ADMIN),
				new Endpoint(HttpMethod.GET, "/api/students/me", Role.STUDENT),
				new Endpoint(HttpMethod.GET, "/api/students/me/drives", Role.STUDENT),
				new Endpoint(HttpMethod.GET, "/api/students/me/applications", Role.STUDENT),
				new Endpoint(HttpMethod.GET, "/api/students/me/applications/1", Role.STUDENT),
				new Endpoint(HttpMethod.POST, "/api/students/me/applications/1/withdraw", Role.STUDENT));
	}

	@ParameterizedTest(name = "no token -> 401: {0}")
	@MethodSource("protectedEndpoints")
	void noTokenIs401WithStandardBody(Endpoint endpoint) throws Exception {
		mvc.perform(call(endpoint))
				.andExpect(status().isUnauthorized())
				.andExpect(header().string("WWW-Authenticate", "Bearer"))
				.andExpect(jsonPath("$.status").value(401))
				.andExpect(jsonPath("$.error").value("Unauthorized"))
				.andExpect(jsonPath("$.message").value("Authentication required"))
				.andExpect(jsonPath("$.path").value(endpoint.path()))
				.andExpect(jsonPath("$.timestamp").exists());
	}

	@ParameterizedTest(name = "invalid token -> 401: {0}")
	@MethodSource("protectedEndpoints")
	void invalidTokenIs401(Endpoint endpoint) throws Exception {
		mvc.perform(call(endpoint).header("Authorization", "Bearer not.a.real-token"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.message").value("Invalid or expired token"));
	}

	@ParameterizedTest(name = "wrong role -> 403: {0}")
	@MethodSource("protectedEndpoints")
	void wrongRoleIs403WithStandardBody(Endpoint endpoint) throws Exception {
		String wrongRoleToken = endpoint.requiredRole() == Role.STUDENT ? adminToken : studentToken;

		mvc.perform(call(endpoint).header("Authorization", "Bearer " + wrongRoleToken))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.status").value(403))
				.andExpect(jsonPath("$.error").value("Forbidden"))
				.andExpect(jsonPath("$.message").value("You do not have permission to access this resource"))
				.andExpect(jsonPath("$.path").value(endpoint.path()));
	}

	@ParameterizedTest(name = "right role -> allowed: {0}")
	@MethodSource("protectedEndpoints")
	void rightRoleIsLetThrough(Endpoint endpoint) throws Exception {
		String rightRoleToken = endpoint.requiredRole() == Role.STUDENT ? studentToken : adminToken;

		MvcResult result = mvc.perform(call(endpoint).header("Authorization", "Bearer " + rightRoleToken)).andReturn();

		assertThat(result.getResponse().getStatus()).as(endpoint.toString()).isNotIn(401, 403);
	}

	// ---------------------------------------------------------------------
	// Token edge cases
	// ---------------------------------------------------------------------

	@Test
	void expiredTokenIs401() throws Exception {
		// issued 3 hours ago with a 2-hour lifetime
		String expired = tokenFor(Role.TPO_ADMIN, Clock.fixed(Instant.now().minus(Duration.ofHours(3)), ZoneOffset.UTC));

		mvc.perform(request(HttpMethod.GET, "/api/companies").header("Authorization", "Bearer " + expired))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.message").value("Invalid or expired token"));
	}

	@Test
	void tokenSignedWithAnotherSecretIs401() throws Exception {
		JwtEncoder attackerEncoder = new JwtTestSupport("an-attacker-secret-that-is-also-32-bytes-or-more").encoder();
		String forged = new TokenService(attackerEncoder, jwtProperties, Clock.systemUTC())
				.issue(new AuthUser(1L, "admin@placementhub.dev", Role.TPO_ADMIN, "x")).value();

		mvc.perform(request(HttpMethod.GET, "/api/companies").header("Authorization", "Bearer " + forged))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void unknownAuthenticatedPathWithoutTokenIs401() throws Exception {
		mvc.perform(request(HttpMethod.GET, "/api/something-else"))
				.andExpect(status().isUnauthorized());
	}

	// ---------------------------------------------------------------------
	// Public endpoints
	// ---------------------------------------------------------------------

	@Test
	void healthIsPublic() throws Exception {
		mvc.perform(request(HttpMethod.GET, "/api/health")).andExpect(status().isOk());
	}

	@Test
	void loginIsPublic() throws Exception {
		MvcResult result = mvc.perform(request(HttpMethod.POST, "/api/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"a@b.dev\",\"password\":\"whatever\"}"))
				.andReturn();

		assertThat(result.getResponse().getStatus()).isNotIn(401, 403);
	}

	@Test
	void registerIsPublic() throws Exception {
		mvc.perform(request(HttpMethod.POST, "/api/auth/register")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"email":"new@student.placementhub.dev","password":"Secret@123","fullName":"New Student",
						 "rollNo":"23CSE123","branch":"CSE","graduationYear":2027,"cgpa":8.0,"activeBacklogs":0}
						"""))
				.andExpect(status().isCreated());
	}

	@Test
	void registerWithShortPasswordIs400() throws Exception {
		mvc.perform(request(HttpMethod.POST, "/api/auth/register")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"email":"new@student.placementhub.dev","password":"short","fullName":"New Student",
						 "rollNo":"23CSE123","branch":"CSE","graduationYear":2027,"cgpa":8.0,"activeBacklogs":0}
						"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("Validation failed: password: size must be between 8 and 72"));
	}

	// ---------------------------------------------------------------------
	// CORS (default allowed origin: http://localhost:5173)
	// ---------------------------------------------------------------------

	@Test
	void preflightFromAllowedOriginIsAnsweredWithoutToken() throws Exception {
		mvc.perform(request(HttpMethod.OPTIONS, "/api/companies")
				.header("Origin", "http://localhost:5173")
				.header("Access-Control-Request-Method", "GET")
				.header("Access-Control-Request-Headers", "authorization"))
				.andExpect(status().isOk())
				.andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"))
				.andExpect(header().exists("Access-Control-Allow-Methods"));
	}

	@Test
	void preflightFromUnknownOriginIsRejected() throws Exception {
		mvc.perform(request(HttpMethod.OPTIONS, "/api/companies")
				.header("Origin", "https://evil.example")
				.header("Access-Control-Request-Method", "GET"))
				.andExpect(status().isForbidden())
				.andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
	}

	@Test
	void actualRequestFromAllowedOriginGetsCorsHeader() throws Exception {
		mvc.perform(request(HttpMethod.GET, "/api/companies")
				.header("Origin", "http://localhost:5173")
				.header("Authorization", "Bearer " + adminToken))
				.andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
	}

	// ---------------------------------------------------------------------
	// helpers
	// ---------------------------------------------------------------------

	private String tokenFor(Role role, Clock clock) {
		return new TokenService(jwtEncoder, jwtProperties, clock)
				.issue(new AuthUser(role == Role.STUDENT ? 2L : 1L, role.name().toLowerCase() + "@placementhub.dev", role,
						"x"))
				.value();
	}

	private static MockHttpServletRequestBuilder call(Endpoint endpoint) {
		return request(endpoint.method(), endpoint.path());
	}

}
