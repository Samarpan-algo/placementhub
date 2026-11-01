package com.placementhub.application;

/** Lightweight query result: a student's application status for one drive (no entities loaded). */
public record DriveApplicationStatus(Long driveId, ApplicationStatus status) {
}
