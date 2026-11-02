package com.placementhub.common;

import java.time.ZoneId;

/** Time constants shared across features. Storage and the API stay UTC; IST is for what people read or enter. */
public final class AppTime {

	/** India Standard Time: used for student-facing messages and for comparing calendar days (e.g. drive dates). */
	public static final ZoneId IST = ZoneId.of("Asia/Kolkata");

	private AppTime() {
	}

}
