package com.placementhub.student;

import java.util.List;

import com.placementhub.application.ApplicationStatus;
import com.placementhub.drive.DriveResponse;

/**
 * A drive as seen by one student: the drive, whether they can apply (criteria met and no
 * placement policy blocks), every reason why not, and their application status (null if they have not applied).
 */
public record StudentDriveResponse(
		DriveResponse drive,
		boolean eligible,
		List<String> reasons,
		ApplicationStatus applicationStatus) {
}
