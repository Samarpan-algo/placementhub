package com.placementhub.resume;

import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.placementhub.common.ExternalServiceException;

/**
 * Keeps resumes in memory and never touches the network. Active only with app.resume.storage=fake (the
 * "test" profile), so JUnit and Postman runs put nothing in the real bucket. It lives in main code because
 * Postman runs against the real app, not the test classpath.
 */
@Component
@ConditionalOnProperty(name = "app.resume.storage", havingValue = "fake")
public class FakeResumeStorage implements ResumeStorage {

	private static final Logger log = LoggerFactory.getLogger(FakeResumeStorage.class);

	private final Map<String, byte[]> files = new ConcurrentHashMap<>();
	private volatile ExternalServiceException failure;

	public FakeResumeStorage() {
		log.warn("Using the FAKE in-memory resume storage: files are not really stored");
	}

	@Override
	public void upload(String path, byte[] pdf) {
		failIfAsked();
		files.put(path, pdf);
	}

	@Override
	public String signedUrl(String path, Duration ttl) {
		failIfAsked();
		return "https://fake-storage.invalid/" + path + "?token=fake&expiresIn=" + ttl.toSeconds();
	}

	@Override
	public void delete(String path) {
		failIfAsked();
		files.remove(path);
	}

	// ---------- for tests ----------

	public Set<String> storedPaths() {
		return Set.copyOf(files.keySet());
	}

	/** Every call throws this until reset() is called, to test how the app handles a storage outage. */
	public void failWith(ExternalServiceException exception) {
		this.failure = exception;
	}

	public void reset() {
		files.clear();
		failure = null;
	}

	private void failIfAsked() {
		if (failure != null) {
			throw failure;
		}
	}

}
