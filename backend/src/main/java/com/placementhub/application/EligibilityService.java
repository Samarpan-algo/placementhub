package com.placementhub.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.placementhub.drive.Drive;
import com.placementhub.drive.DriveStatus;
import com.placementhub.student.StudentProfile;

/**
 * Decides whether a student may apply to a drive, and explains why not.
 * Pure logic: no repositories, no clock. The caller passes "now" and objects whose
 * fields (including drive branches) are already loaded.
 */
@Service
public class EligibilityService {

	/** Deadlines in messages are shown in Indian time, because students read them. */
	private static final ZoneId STUDENT_ZONE = ZoneId.of("Asia/Kolkata");
	private static final DateTimeFormatter DEADLINE_FORMAT =
			DateTimeFormatter.ofPattern("d MMM yyyy, h:mm a 'IST'", Locale.ENGLISH).withZone(STUDENT_ZONE);

	public EligibilityResult evaluate(StudentProfile student, Drive drive, Instant now) {
		List<String> reasons = new ArrayList<>();

		// 1. Drive must be accepting applications
		if (drive.getStatus() != DriveStatus.OPEN) {
			reasons.add("This drive is not open for applications (status: " + drive.getStatus() + ")");
		}

		// 2. Registration must still be open: "now" strictly before the deadline
		if (!now.isBefore(drive.getRegistrationDeadline())) {
			reasons.add("Registration closed on " + DEADLINE_FORMAT.format(drive.getRegistrationDeadline()));
		}

		// 3. Student's branch must be one of the drive's branches
		if (!drive.getBranches().contains(student.getBranch())) {
			String allowed = drive.getBranches().stream().sorted().map(Enum::name).collect(Collectors.joining(", "));
			reasons.add("Open only to " + allowed + "; your branch is " + student.getBranch());
		}

		// 4. CGPA at least the minimum (compareTo, not equals: 7.5 and 7.50 are equal here)
		if (student.getCgpa().compareTo(drive.getMinCgpa()) < 0) {
			reasons.add("Minimum CGPA is " + twoDecimals(drive.getMinCgpa()) + "; yours is "
					+ twoDecimals(student.getCgpa()));
		}

		// 5. Active backlogs at most the maximum
		int backlogs = student.getActiveBacklogs();
		int maxBacklogs = drive.getMaxBacklogs();
		if (backlogs > maxBacklogs) {
			String limit = (maxBacklogs == 0)
					? "No active backlogs allowed"
					: "At most " + maxBacklogs + " active " + plural(maxBacklogs, "backlog") + " allowed";
			reasons.add(limit + "; you have " + backlogs);
		}

		// 6. Graduation year must match the drive's batch
		if (!student.getGraduationYear().equals(drive.getGraduationYear())) {
			reasons.add("Only for the " + drive.getGraduationYear() + " batch; you graduate in "
					+ student.getGraduationYear());
		}

		return EligibilityResult.of(reasons);
	}

	private static String twoDecimals(BigDecimal value) {
		return value.setScale(2, RoundingMode.HALF_UP).toPlainString();
	}

	private static String plural(int count, String word) {
		return count == 1 ? word : word + "s";
	}

}
