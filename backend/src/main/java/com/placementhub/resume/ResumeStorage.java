package com.placementhub.resume;

import java.time.Duration;

/**
 * Where resume files live. The rest of the app knows only this interface, not Supabase.
 * app.resume.storage chooses the implementation: "supabase" (real) or "fake" (tests and Postman).
 * Every method throws ExternalServiceException (502/503) when the storage fails.
 */
public interface ResumeStorage {

	void upload(String path, byte[] pdf);

	/** A link that works without logging in, for a limited time. Anyone holding it can read the file. */
	String signedUrl(String path, Duration ttl);

	void delete(String path);

}
