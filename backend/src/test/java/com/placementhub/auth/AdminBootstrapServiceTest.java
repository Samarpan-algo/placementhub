package com.placementhub.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.placementhub.user.Role;
import com.placementhub.user.User;
import com.placementhub.user.UserRepository;

class AdminBootstrapServiceTest {

	private static final String PASSWORD = "Str0ng-Admin-Pass";

	private final UserRepository userRepository = mock(UserRepository.class);
	private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
	private final AdminBootstrapService service = new AdminBootstrapService(userRepository, passwordEncoder);

	@Test
	void createsTheAdminWithAHashedPasswordWhenNoneExists() {
		when(passwordEncoder.encode(PASSWORD)).thenReturn("bcrypt-hash");

		assertThat(service.ensureAdmin("  TPO@College.edu ", PASSWORD)).isEqualTo(AdminBootstrapService.Outcome.CREATED);

		ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
		verify(userRepository).save(saved.capture());
		assertThat(saved.getValue().getEmail()).isEqualTo("tpo@college.edu");
		assertThat(saved.getValue().getRole()).isEqualTo(Role.TPO_ADMIN);
		assertThat(saved.getValue().getPasswordHash()).isEqualTo("bcrypt-hash").isNotEqualTo(PASSWORD);
	}

	@Test
	void skipsWhenAnAdminAlreadyExistsEvenWithoutTheVariables() {
		when(userRepository.existsByRole(Role.TPO_ADMIN)).thenReturn(true);

		assertThat(service.ensureAdmin(null, null)).isEqualTo(AdminBootstrapService.Outcome.ALREADY_EXISTS);

		verify(userRepository, never()).save(any());
		verify(passwordEncoder, never()).encode(anyString());
	}

	@Test
	void runningTwiceCreatesOnlyOneAdmin() {
		when(passwordEncoder.encode(PASSWORD)).thenReturn("bcrypt-hash");
		when(userRepository.existsByRole(Role.TPO_ADMIN)).thenReturn(false, true);

		assertThat(service.ensureAdmin("tpo@college.edu", PASSWORD)).isEqualTo(AdminBootstrapService.Outcome.CREATED);
		assertThat(service.ensureAdmin("tpo@college.edu", PASSWORD))
				.isEqualTo(AdminBootstrapService.Outcome.ALREADY_EXISTS);

		verify(userRepository).save(any());
	}

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = "   ")
	void noAdminAndNoEmailStopsStartup(String email) {
		assertThatThrownBy(() -> service.ensureAdmin(email, PASSWORD))
				.isInstanceOf(IllegalStateException.class)
				.hasMessage("No TPO admin exists yet: set ADMIN_EMAIL and ADMIN_PASSWORD so one can be created");
	}

	@ParameterizedTest
	@NullAndEmptySource
	void noAdminAndNoPasswordStopsStartup(String password) {
		assertThatThrownBy(() -> service.ensureAdmin("tpo@college.edu", password))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("set ADMIN_EMAIL and ADMIN_PASSWORD");
	}

	@ParameterizedTest
	@ValueSource(strings = { "not-an-email", "a@b", "a b@c.de" })
	void anInvalidEmailStopsStartup(String email) {
		assertThatThrownBy(() -> service.ensureAdmin(email, PASSWORD))
				.isInstanceOf(IllegalStateException.class)
				.hasMessage("ADMIN_EMAIL is not a valid email address");
	}

	@Test
	void aShortPasswordStopsStartupWithoutShowingIt() {
		assertThatThrownBy(() -> service.ensureAdmin("tpo@college.edu", "Short1!"))
				.isInstanceOf(IllegalStateException.class)
				.hasMessage("ADMIN_PASSWORD must be 8 to 72 characters long")
				.message().doesNotContain("Short1!");
		verify(userRepository, never()).save(any());
	}

	@Test
	void aPasswordOver72BytesStopsStartup() {
		assertThatThrownBy(() -> service.ensureAdmin("tpo@college.edu", "x".repeat(73)))
				.isInstanceOf(IllegalStateException.class)
				.hasMessage("ADMIN_PASSWORD must be 8 to 72 characters long");
	}

	@Test
	void anEmailAlreadyUsedByAStudentStopsStartup() {
		when(userRepository.existsByEmail("student@college.edu")).thenReturn(true);

		assertThatThrownBy(() -> service.ensureAdmin("student@college.edu", PASSWORD))
				.isInstanceOf(IllegalStateException.class)
				.hasMessage("ADMIN_EMAIL is already used by another account");
		verify(userRepository, never()).save(any());
	}

}
