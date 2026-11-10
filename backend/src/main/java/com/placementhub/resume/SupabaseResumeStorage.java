package com.placementhub.resume;

import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

import com.placementhub.common.ExternalServiceException;

/**
 * The private Supabase Storage bucket, through its REST API (no SDK).
 *
 * Secrets: the secret key is sent only in request headers to Supabase. Logs say what failed and the HTTP
 * status, never the key, a signed URL or Supabase's response, and clients get only our own messages.
 */
@Component
@ConditionalOnProperty(name = "app.resume.storage", havingValue = "supabase")
class SupabaseResumeStorage implements ResumeStorage {

	private static final Logger log = LoggerFactory.getLogger(SupabaseResumeStorage.class);

	private static final int CONNECT_TIMEOUT_MS = 3_000;
	private static final int READ_TIMEOUT_MS = 15_000;

	static final String FAILED_MESSAGE = "The resume storage service returned an error. Please try again.";
	static final String UNAVAILABLE_MESSAGE = "The resume storage service is unavailable. Please try again later.";

	private final ResumeProperties.Supabase settings;
	private final String baseUrl;
	private final RestClient client;

	@Autowired
	SupabaseResumeStorage(ResumeProperties properties) {
		this(properties, RestClient.builder().requestFactory(timeouts()));
	}

	/** Tests pass a builder bound to a mock server, so no test ever reaches Supabase. */
	SupabaseResumeStorage(ResumeProperties properties, RestClient.Builder builder) {
		this.settings = properties.supabase();
		if (settings.isConfigured()) {
			this.baseUrl = settings.url().replaceAll("/+$", "");
			// Supabase's newer secret keys go in "apikey"; Authorization may carry only the same value
			this.client = builder.defaultHeader("apikey", settings.secretKey())
					.defaultHeader("Authorization", "Bearer " + settings.secretKey())
					.build();
		} else {
			this.baseUrl = null;
			this.client = null;
			log.warn("SUPABASE_URL, SUPABASE_SECRET_KEY or SUPABASE_RESUME_BUCKET is not set: "
					+ "resume upload and viewing will answer 503");
		}
	}

	private static SimpleClientHttpRequestFactory timeouts() {
		SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
		factory.setConnectTimeout(CONNECT_TIMEOUT_MS);
		factory.setReadTimeout(READ_TIMEOUT_MS);
		return factory;
	}

	@Override
	public void upload(String path, byte[] pdf) {
		call("upload", () -> client.post()
				.uri(objectUri(path, "object"))
				.contentType(MediaType.APPLICATION_PDF)
				.body(pdf)
				.retrieve()
				.toBodilessEntity());
	}

	@Override
	public String signedUrl(String path, Duration ttl) {
		SignResponse response = call("sign", () -> client.post()
				.uri(objectUri(path, "object", "sign"))
				.contentType(MediaType.APPLICATION_JSON)
				.body(Map.of("expiresIn", ttl.toSeconds()))
				.retrieve()
				.body(SignResponse.class));
		if (response == null || response.signedURL() == null || response.signedURL().isBlank()) {
			log.warn("Supabase sign returned no URL");
			throw ExternalServiceException.badGateway(FAILED_MESSAGE);
		}
		// Supabase answers with a link relative to /storage/v1
		return baseUrl + "/storage/v1" + response.signedURL();
	}

	@Override
	public void delete(String path) {
		call("delete", () -> client.delete()
				.uri(objectUri(path, "object"))
				.retrieve()
				.toBodilessEntity());
	}

	/** Runs one Supabase call and turns every failure into our own 502 or 503. */
	private <T> T call(String operation, Supplier<T> request) {
		if (!settings.isConfigured()) {
			throw ExternalServiceException.unavailable(UNAVAILABLE_MESSAGE);
		}
		try {
			return request.get();
		} catch (RestClientResponseException ex) {
			log.warn("Supabase {} failed with HTTP {}", operation, ex.getStatusCode().value());
			throw ExternalServiceException.badGateway(FAILED_MESSAGE);
		} catch (ResourceAccessException ex) {
			log.warn("Supabase {} could not reach the service", operation);
			throw ExternalServiceException.unavailable(UNAVAILABLE_MESSAGE);
		} catch (RestClientException ex) {
			log.warn("Supabase {} gave an unreadable answer", operation);
			throw ExternalServiceException.badGateway(FAILED_MESSAGE);
		}
	}

	/** {base}/storage/v1/object[/sign]/{bucket}/students/1/x.pdf, each piece URL-encoded on its own. */
	private URI objectUri(String path, String... operation) {
		return UriComponentsBuilder.fromUriString(baseUrl)
				.pathSegment("storage", "v1")
				.pathSegment(operation)
				.pathSegment(settings.bucket())
				.pathSegment(path.split("/"))
				.build()
				.encode()
				.toUri();
	}

	private record SignResponse(String signedURL) {
	}

}
