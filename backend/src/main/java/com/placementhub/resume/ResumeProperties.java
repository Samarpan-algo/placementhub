package com.placementhub.resume;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Resume settings from application.yml (app.resume.*). */
@ConfigurationProperties(prefix = "app.resume")
public record ResumeProperties(String storage, Duration signedUrlTtl, Supabase supabase) {

	public record Supabase(String url, String secretKey, String bucket) {

		boolean isConfigured() {
			return isSet(url) && isSet(secretKey) && isSet(bucket);
		}

		private static boolean isSet(String value) {
			return value != null && !value.isBlank();
		}

		/** The generated toString would print the secret key if this were ever logged. */
		@Override
		public String toString() {
			return "Supabase[url=" + url + ", bucket=" + bucket + ", secretKey=<hidden>]";
		}

	}

}
