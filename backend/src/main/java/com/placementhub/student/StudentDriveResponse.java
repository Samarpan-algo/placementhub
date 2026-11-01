package com.placementhub.student;

import java.util.List;

import com.placementhub.drive.DriveResponse;

/** A drive as seen by one student: the drive plus whether they are eligible, and why not. */
public record StudentDriveResponse(DriveResponse drive, boolean eligible, List<String> reasons) {
}
