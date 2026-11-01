package com.placementhub.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.util.ReflectionTestUtils;

import com.placementhub.common.Branch;
import com.placementhub.common.ConflictException;
import com.placementhub.config.JwtProperties;
import com.placementhub.student.StudentProfile;
import com.placementhub.student.StudentProfileMapper;
import com.placementhub.student.StudentProfileRepository;
import com.placementhub.student.StudentProfileResponse;
import com.placementhub.user.Role;
import com.placementhub.user.User;
import com.placementhub.user.UserRepository;

/**
 * Registration rules with mocked repositories; login uses the REAL DaoAuthenticationProvider
 * and BCrypt, so the "same 401 for unknown email and wrong password" behaviour is really tested.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

	private static final String SECRET = "test-secret-that-is-definitely-at-least-32-bytes-long";
	private static final Instant NOW = Instant.parse("2026-11-01T10:00:00Z");

	@Mock
	private UserRepository userRepository;
	@Mock
	private StudentProfileRepository studentProfileRepository;

	/** Low cost keeps the tests fast; production uses cost 10. */
	private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);
	private AuthService authService;

	@BeforeEach
	void setUp() {
		JwtTestSupport jwt = new JwtTestSupport(SECRET);
		TokenService tokenService = new TokenService(jwt.encoder(),
				new JwtProperties(SECRET, Duration.ofHours(2), JwtTestSupport.ISSUER), Clock.fixed(NOW, ZoneOffset.UTC));

		DaoAuthenticationProvider provider = new DaoAuthenticationProvider(new AuthUserDetailsService(userRepository));
		provider.setPasswordEncoder(passwordEncoder);

		authService = new AuthService(userRepository, studentProfileRepository, new StudentProfileMapper(),
				passwordEncoder, new ProviderManager(provider), tokenService);
	}

	// =====================================================================
	// register
	// =====================================================================

	@Nested
	class Register {

		@Test
		void createsStudentUserWithHashedPasswordAndNormalisedEmailAndRollNo() {
			when(userRepository.existsByEmail("neha.kapoor@student.placementhub.dev")).thenReturn(false);
			when(studentProfileRepository.existsByRollNo("23CSE050")).thenReturn(false);

			StudentProfileResponse response = authService.register(request("  Neha.Kapoor@Student.PlacementHub.dev ",
					"Secret@123", " 23cse050 "));

			ArgumentCaptor<User> savedUser = ArgumentCaptor.forClass(User.class);
			verify(userRepository).saveAndFlush(savedUser.capture());
			User user = savedUser.getValue();
			assertThat(user.getEmail()).isEqualTo("neha.kapoor@student.placementhub.dev");
			assertThat(user.getRole()).isEqualTo(Role.STUDENT);
			assertThat(user.getPasswordHash()).isNotEqualTo("Secret@123").startsWith("$2a$");
			assertThat(passwordEncoder.matches("Secret@123", user.getPasswordHash())).isTrue();

			ArgumentCaptor<StudentProfile> savedProfile = ArgumentCaptor.forClass(StudentProfile.class);
			verify(studentProfileRepository).saveAndFlush(savedProfile.capture());
			assertThat(savedProfile.getValue().getUser()).isSameAs(user);

			assertThat(response.email()).isEqualTo("neha.kapoor@student.placementhub.dev");
			assertThat(response.rollNo()).isEqualTo("23CSE050");
			assertThat(response.cgpa()).isEqualByComparingTo("8.10");
			assertThat(response.branch()).isEqualTo(Branch.CSE);
		}

		@Test
		void duplicateEmailIs409AndNothingIsSaved() {
			when(userRepository.existsByEmail("aarav.sharma@student.placementhub.dev")).thenReturn(true);

			assertThatThrownBy(() -> authService.register(
					request("AARAV.SHARMA@student.placementhub.dev", "Secret@123", "23CSE777")))
					.isInstanceOf(ConflictException.class)
					.hasMessage("An account with this email already exists");
			verify(userRepository, never()).saveAndFlush(any());
		}

		@Test
		void duplicateRollNoIs409AndNothingIsSaved() {
			when(userRepository.existsByEmail(any())).thenReturn(false);
			when(studentProfileRepository.existsByRollNo("23CSE001")).thenReturn(true);

			assertThatThrownBy(() -> authService.register(request("new@student.placementhub.dev", "Secret@123",
					"23cse001")))
					.isInstanceOf(ConflictException.class)
					.hasMessage("This roll number is already registered");
			verify(userRepository, never()).saveAndFlush(any());
		}

		@Test
		void concurrentDuplicateEmailCaughtByConstraintIs409() {
			when(userRepository.existsByEmail(any())).thenReturn(false);
			when(studentProfileRepository.existsByRollNo(any())).thenReturn(false);
			when(userRepository.saveAndFlush(any(User.class))).thenThrow(constraintViolation("uq_users_email"));

			assertThatThrownBy(() -> authService.register(request("race@student.placementhub.dev", "Secret@123",
					"23CSE900")))
					.isInstanceOf(ConflictException.class)
					.hasMessage("An account with this email already exists");
		}

		@Test
		void concurrentDuplicateRollNoCaughtByConstraintIs409() {
			when(userRepository.existsByEmail(any())).thenReturn(false);
			when(studentProfileRepository.existsByRollNo(any())).thenReturn(false);
			when(studentProfileRepository.saveAndFlush(any(StudentProfile.class)))
					.thenThrow(constraintViolation("uq_student_profiles_roll_no"));

			assertThatThrownBy(() -> authService.register(request("race2@student.placementhub.dev", "Secret@123",
					"23CSE901")))
					.isInstanceOf(ConflictException.class)
					.hasMessage("This roll number is already registered");
		}

		@Test
		void registerRequestHasNoRoleSoEveryAccountIsAStudent() {
			// Structural guarantee: the DTO cannot carry a role, so "role": "TPO_ADMIN" in JSON is ignored
			assertThat(RegisterRequest.class.getRecordComponents())
					.extracting(java.lang.reflect.RecordComponent::getName)
					.doesNotContain("role");
		}

	}

	// =====================================================================
	// login
	// =====================================================================

	@Nested
	class Login {

		private User aarav;

		@BeforeEach
		void seedUser() {
			aarav = new User("aarav.sharma@student.placementhub.dev", passwordEncoder.encode("Demo@1234"),
					Role.STUDENT);
			ReflectionTestUtils.setField(aarav, "id", 2L);
			lenient().when(userRepository.findByEmail("aarav.sharma@student.placementhub.dev"))
					.thenReturn(Optional.of(aarav));
			lenient().when(userRepository.findByEmail("nobody@student.placementhub.dev"))
					.thenReturn(Optional.empty());
		}

		@Test
		void correctPasswordReturnsBearerTokenForTheUser() {
			LoginResponse response = authService.login(new LoginRequest(" Aarav.Sharma@student.placementhub.dev ",
					"Demo@1234"));

			assertThat(response.tokenType()).isEqualTo("Bearer");
			assertThat(response.expiresIn()).isEqualTo(7200);
			assertThat(response.role()).isEqualTo(Role.STUDENT);

			// Decode with a decoder whose clock is "now" (the token was issued at the fixed NOW)
			Jwt jwt = JwtTestSupport.decoderAt(SECRET, NOW.plusSeconds(60)).decode(response.accessToken());
			assertThat(jwt.getSubject()).isEqualTo("2");
			assertThat(jwt.getClaimAsString("role")).isEqualTo("STUDENT");
		}

		@Test
		void wrongPasswordIsBadCredentials() {
			assertThatThrownBy(() -> authService.login(
					new LoginRequest("aarav.sharma@student.placementhub.dev", "wrong-password")))
					.isInstanceOf(BadCredentialsException.class);
		}

		@Test
		void unknownEmailIsTheSameBadCredentialsNotUserNotFound() {
			// Spring hides "user not found" as BadCredentialsException, so both cases get the same 401
			assertThatThrownBy(() -> authService.login(new LoginRequest("nobody@student.placementhub.dev",
					"Demo@1234")))
					.isExactlyInstanceOf(BadCredentialsException.class);
		}

	}

	// ---------------------------------------------------------------------
	// helpers
	// ---------------------------------------------------------------------

	private static RegisterRequest request(String email, String password, String rollNo) {
		return new RegisterRequest(email, password, " Neha Kapoor ", rollNo, Branch.CSE, 2027,
				new BigDecimal("8.10"), 0, "9876500050");
	}

	private static DataIntegrityViolationException constraintViolation(String constraintName) {
		return new DataIntegrityViolationException("could not execute statement",
				new ConstraintViolationException("duplicate key", new SQLException("23505"), constraintName));
	}

}
