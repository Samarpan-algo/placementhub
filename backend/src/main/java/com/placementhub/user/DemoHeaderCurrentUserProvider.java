package com.placementhub.user;

import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.placementhub.common.BadRequestException;
import com.placementhub.common.ResourceNotFoundException;

/**
 * Temporary identity for demos: the caller is the user whose id is in the X-Demo-User-Id header,
 * or the seeded demo admin if the header is missing. Anyone can send this header, so it is NOT secure.
 * TODO Milestone 5: delete this class and add a JWT-based CurrentUserProvider.
 */
@Component
public class DemoHeaderCurrentUserProvider implements CurrentUserProvider {

	public static final String HEADER = "X-Demo-User-Id";
	static final String DEMO_ADMIN_EMAIL = "admin@placementhub.dev";

	private final UserRepository userRepository;

	public DemoHeaderCurrentUserProvider(UserRepository userRepository) {
		this.userRepository = userRepository;
	}

	@Override
	public Long currentUserId() {
		String header = currentRequestHeader();
		if (header == null || header.isBlank()) {
			return demoAdminId();
		}
		long userId;
		try {
			userId = Long.parseLong(header.trim());
		} catch (NumberFormatException e) {
			throw new BadRequestException(HEADER + " must be a number");
		}
		if (!userRepository.existsById(userId)) {
			throw ResourceNotFoundException.of("User", userId);
		}
		return userId;
	}

	private Long demoAdminId() {
		return userRepository.findByEmail(DEMO_ADMIN_EMAIL)
				.map(User::getId)
				.orElseThrow(() -> new IllegalStateException("Demo admin " + DEMO_ADMIN_EMAIL + " is not seeded"));
	}

	/** The header from the current HTTP request, or null when called outside a request. */
	private static String currentRequestHeader() {
		if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
			return attributes.getRequest().getHeader(HEADER);
		}
		return null;
	}

}
