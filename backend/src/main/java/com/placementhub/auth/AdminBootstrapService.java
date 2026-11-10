package com.placementhub.auth;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Pattern;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.placementhub.user.Role;
import com.placementhub.user.User;
import com.placementhub.user.UserRepository;

/**
 * Creates the first TPO admin from ADMIN_EMAIL / ADMIN_PASSWORD in production, where there is no demo seed.
 * Idempotent: once any admin exists it does nothing, so it is safe on every restart.
 * Error messages never contain the password.
 */
@Service
public class AdminBootstrapService {

	public enum Outcome {
		CREATED, ALREADY_EXISTS
	}

	private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
	/** The same limits as registration. BCrypt uses only the first 72 bytes. */
	static final int MIN_PASSWORD_LENGTH = 8;
	static final int MAX_PASSWORD_BYTES = 72;

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;

	public AdminBootstrapService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
		this.userRepository = userRepository;
		this.passwordEncoder = passwordEncoder;
	}

	@Transactional
	public Outcome ensureAdmin(String email, String password) {
		if (userRepository.existsByRole(Role.TPO_ADMIN)) {
			return Outcome.ALREADY_EXISTS;
		}
		String normalizedEmail = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
		if (normalizedEmail.isEmpty() || password == null || password.isEmpty()) {
			throw new IllegalStateException(
					"No TPO admin exists yet: set ADMIN_EMAIL and ADMIN_PASSWORD so one can be created");
		}
		if (!EMAIL.matcher(normalizedEmail).matches()) {
			throw new IllegalStateException("ADMIN_EMAIL is not a valid email address");
		}
		if (password.length() < MIN_PASSWORD_LENGTH
				|| password.getBytes(StandardCharsets.UTF_8).length > MAX_PASSWORD_BYTES) {
			throw new IllegalStateException("ADMIN_PASSWORD must be 8 to 72 characters long");
		}
		if (userRepository.existsByEmail(normalizedEmail)) {
			throw new IllegalStateException("ADMIN_EMAIL is already used by another account");
		}
		userRepository.save(new User(normalizedEmail, passwordEncoder.encode(password), Role.TPO_ADMIN));
		return Outcome.CREATED;
	}

}
