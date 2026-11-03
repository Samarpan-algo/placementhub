package com.placementhub.student;

import java.util.List;

import com.placementhub.application.ApplicationStatus;
import com.placementhub.drive.DriveResponse;

/**
 * A drive as seen by one student: the drive, whether they can apply (criteria met and no
 * placement policy blocks), every reason why not, and their application id and status (both null if they have
 * not applied). The id lets the frontend link to the application or withdraw it from the drive page.
 */
public record StudentDriveResponse(
		DriveResponse drive,
		boolean eligible,
		List<String> reasons,
		Long applicationId,
		ApplicationStatus applicationStatus) {
}
