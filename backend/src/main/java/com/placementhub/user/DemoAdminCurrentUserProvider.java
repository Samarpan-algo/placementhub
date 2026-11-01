package com.placementhub.user;

import org.springframework.stereotype.Component;

/**
 * Temporary: every request acts as the seeded demo admin.
 * TODO Milestone 5: delete this class and add a JWT-based CurrentUserProvider.
 */
@Component
public class DemoAdminCurrentUserProvider implements CurrentUserProvider {

	static final String DEMO_ADMIN_EMAIL = "admin@placementhub.dev";

	private final UserRepository userRepository;

	public DemoAdminCurrentUserProvider(UserRepository userRepository) {
		this.userRepository = userRepository;
	}

	@Override
	public Long currentUserId() {
		return userRepository.findByEmail(DEMO_ADMIN_EMAIL)
				.map(User::getId)
				.orElseThrow(() -> new IllegalStateException("Demo admin " + DEMO_ADMIN_EMAIL + " is not seeded"));
	}

}
