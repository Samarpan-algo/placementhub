package com.placementhub.auth;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.placementhub.common.ConflictException;
import com.placementhub.common.ConstraintViolations;
import com.placementhub.student.StudentProfile;
import com.placementhub.student.StudentProfileMapper;
import com.placementhub.student.StudentProfileRepository;
import com.placementhub.student.StudentProfileResponse;
import com.placementhub.user.Role;
import com.placementhub.user.User;
import com.placementhub.user.UserRepository;

/** Student self-registration and login for every role. */
@Service
public class AuthService {

	static final String EMAIL_TAKEN = "An account with this email already exists";
	static final String ROLL_NO_TAKEN = "This roll number is already registered";

	private final UserRepository userRepository;
	private final StudentProfileRepository studentProfileRepository;
	private final StudentProfileMapper studentProfileMapper;
	private final PasswordEncoder passwordEncoder;
	private final AuthenticationManager authenticationManager;
	private final TokenService tokenService;

	public AuthService(UserRepository userRepository, StudentProfileRepository studentProfileRepository,
			StudentProfileMapper studentProfileMapper, PasswordEncoder passwordEncoder,
			AuthenticationManager authenticationManager, TokenService tokenService) {
		this.userRepository = userRepository;
		this.studentProfileRepository = studentProfileRepository;
		this.studentProfileMapper = studentProfileMapper;
		this.passwordEncoder = passwordEncoder;
		this.authenticationManager = authenticationManager;
		this.tokenService = tokenService;
	}

	/** Creates the user (always STUDENT) and the student profile in one transaction. */
	@Transactional
	public StudentProfileResponse register(RegisterRequest request) {
		// email and rollNo are already normalised (lowercase / uppercase) by RegisterRequest
		if (userRepository.existsByEmail(request.email())) {
			throw new ConflictException(EMAIL_TAKEN);
		}
		if (studentProfileRepository.existsByRollNo(request.rollNo())) {
			throw new ConflictException(ROLL_NO_TAKEN);
		}

		User user = new User(request.email(), passwordEncoder.encode(request.password()), Role.STUDENT);
		StudentProfile profile = new StudentProfile(user, request.fullName(), request.rollNo(), request.branch(),
				request.graduationYear().shortValue(), request.cgpa(), request.activeBacklogs().shortValue());
		profile.setPhone(request.phone());

		try {
			userRepository.saveAndFlush(user);
			studentProfileRepository.saveAndFlush(profile);
		} catch (DataIntegrityViolationException e) {
			// Two registrations raced past the checks above; the UNIQUE constraints stopped the second
			if (ConstraintViolations.isViolationOf(e, "uq_users_email")) {
				throw new ConflictException(EMAIL_TAKEN);
			}
			if (ConstraintViolations.isViolationOf(e, "uq_student_profiles_roll_no")) {
				throw new ConflictException(ROLL_NO_TAKEN);
			}
			throw e;
		}
		return studentProfileMapper.toResponse(profile);
	}

	/**
	 * Checks the password with Spring's DaoAuthenticationProvider and issues a token.
	 * Unknown email and wrong password both throw BadCredentialsException (-> 401, same message).
	 */
	public LoginResponse login(LoginRequest request) {
		Authentication authentication = authenticationManager.authenticate(
				UsernamePasswordAuthenticationToken.unauthenticated(request.email(), request.password()));
		AuthUser user = (AuthUser) authentication.getPrincipal();
		TokenService.IssuedToken token = tokenService.issue(user);
		return new LoginResponse(token.value(), "Bearer", token.expiresInSeconds(), user.role());
	}

}
