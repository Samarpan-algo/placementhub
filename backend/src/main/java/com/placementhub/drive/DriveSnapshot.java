package com.placementhub.drive;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.placementhub.common.Branch;

/**
 * The editable fields of a drive as plain values, to find what an edit changed and to audit it as JSON.
 * Values are normalised so that "the same" compares equal: decimals always have 2 places (12 = 12.00),
 * branches are a sorted list, and dates are ISO strings.
 */
final class DriveSnapshot {

	/** The fields EligibilityService reads. Only a change to one of these re-checks applications. */
	static final Set<String> ELIGIBILITY_FIELDS = Set.of("branches", "minCgpa", "maxBacklogs", "graduationYear");

	private DriveSnapshot() {
	}

	/** LinkedHashMap, not Map.of: optional fields may be null, and the JSON keys keep a fixed order. */
	static Map<String, Object> of(Drive drive) {
		Map<String, Object> fields = new LinkedHashMap<>();
		fields.put("roleTitle", drive.getRoleTitle());
		fields.put("jobType", name(drive.getJobType()));
		fields.put("ctcLpa", twoDecimals(drive.getCtcLpa()));
		fields.put("location", drive.getLocation());
		fields.put("description", drive.getDescription());
		fields.put("minCgpa", twoDecimals(drive.getMinCgpa()));
		fields.put("maxBacklogs", drive.getMaxBacklogs() == null ? null : drive.getMaxBacklogs().intValue());
		fields.put("graduationYear", drive.getGraduationYear() == null ? null : drive.getGraduationYear().intValue());
		fields.put("registrationDeadline", Objects.toString(drive.getRegistrationDeadline(), null));
		fields.put("driveDate", Objects.toString(drive.getDriveDate(), null));
		fields.put("branches", drive.getBranches().stream().sorted().map(Branch::name).toList());
		return fields;
	}

	/** Only the fields whose value differs, keeping `from`'s values (call twice to get old and new). */
	static Map<String, Object> changed(Map<String, Object> from, Map<String, Object> other) {
		Map<String, Object> changed = new LinkedHashMap<>();
		from.forEach((field, value) -> {
			if (!Objects.equals(value, other.get(field))) {
				changed.put(field, value);
			}
		});
		return changed;
	}

	private static BigDecimal twoDecimals(BigDecimal value) {
		return value == null ? null : value.setScale(2, RoundingMode.HALF_UP);
	}

	private static String name(Enum<?> value) {
		return value == null ? null : value.name();
	}

}
