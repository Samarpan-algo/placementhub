package com.placementhub.auth;

import java.util.Collection;
import java.util.List;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import com.placementhub.user.Role;
import com.placementhub.user.User;

/** The login-time view of a user that Spring Security's DaoAuthenticationProvider works with. */
public record AuthUser(Long id, String email, Role role, String passwordHash) implements UserDetails {

	public static AuthUser from(User user) {
		return new AuthUser(user.getId(), user.getEmail(), user.getRole(), user.getPasswordHash());
	}

	@Override
	public Collection<? extends GrantedAuthority> getAuthorities() {
		return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
	}

	@Override
	public String getPassword() {
		return passwordHash;
	}

	@Override
	public String getUsername() {
		return email;
	}

}
