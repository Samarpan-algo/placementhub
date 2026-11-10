package com.placementhub.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Production only: makes sure a TPO admin exists once the app has started (after Flyway).
 * If it throws, startup fails with its message: better than a live app nobody can administer.
 */
@Component
@Profile("prod")
class AdminBootstrap implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

	private final AdminBootstrapService adminBootstrapService;
	private final String email;
	private final String password;

	AdminBootstrap(AdminBootstrapService adminBootstrapService, @Value("${app.admin.email:}") String email,
			@Value("${app.admin.password:}") String password) {
		this.adminBootstrapService = adminBootstrapService;
		this.email = email;
		this.password = password;
	}

	@Override
	public void run(ApplicationArguments args) {
		switch (adminBootstrapService.ensureAdmin(email, password)) {
			case CREATED -> log.info("Created the TPO admin account {} from ADMIN_EMAIL", email.trim());
			case ALREADY_EXISTS -> log.info("A TPO admin already exists, skipping admin creation");
		}
	}

}
