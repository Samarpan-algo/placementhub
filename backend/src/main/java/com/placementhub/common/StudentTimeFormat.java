package com.placementhub.common;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Formats instants for messages students read: Indian time, e.g. "11 Nov 2026, 3:30 PM IST". Storage stays UTC. */
public final class StudentTimeFormat {

	/** India Standard Time: the one zone the app uses for dates people read or enter (e.g. drive date checks). */
	public static final ZoneId IST = ZoneId.of("Asia/Kolkata");

	private static final DateTimeFormatter FORMAT =
			DateTimeFormatter.ofPattern("d MMM yyyy, h:mm a 'IST'", Locale.ENGLISH).withZone(IST);

	private StudentTimeFormat() {
	}

	public static String format(Instant instant) {
		return FORMAT.format(instant);
	}

}
