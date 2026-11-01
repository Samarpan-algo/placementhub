package com.placementhub.user;

/**
 * Tells services who is making the current request.
 * Services depend on this interface, not on how the user is identified
 * (implemented by JwtCurrentUserProvider; mocked in unit tests).
 */
public interface CurrentUserProvider {

	Long currentUserId();

}
