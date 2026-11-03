package com.placementhub.application;

/** Lightweight query result: a student's application (id and status) for one drive (no entities loaded). */
public record DriveApplicationStatus(Long driveId, Long applicationId, ApplicationStatus status) {
}
