package com.placementhub.user;

/**
 * Tells services who is making the current request.
 * Services depend on this interface, not on how the user is identified (demo admin now, JWT later).
 */
public interface CurrentUserProvider {

	Long currentUserId();

}
