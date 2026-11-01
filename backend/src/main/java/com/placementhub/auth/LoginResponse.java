package com.placementhub.auth;

import com.placementhub.user.Role;

/** Send the token back as "Authorization: Bearer <accessToken>". expiresIn is in seconds. */
public record LoginResponse(String accessToken, String tokenType, long expiresIn, Role role) {
}
