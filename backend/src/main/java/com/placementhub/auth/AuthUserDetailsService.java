package com.placementhub.auth;

import java.util.Locale;

import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.placementhub.user.UserRepository;

/**
 * Loads users by email for login. "Not found" is turned into the same BadCredentialsException
 * as a wrong password by DaoAuthenticationProvider, so callers can't tell which emails exist.
 */
@Service
public class AuthUserDetailsService implements UserDetailsService {

	private final UserRepository userRepository;

	public AuthUserDetailsService(UserRepository userRepository) {
		this.userRepository = userRepository;
	}

	@Override
	@Transactional(readOnly = true)
	public AuthUser loadUserByUsername(String email) {
		String normalized = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
		return userRepository.findByEmail(normalized)
				.map(AuthUser::from)
				.orElseThrow(() -> new UsernameNotFoundException("No user with this email"));
	}

}
