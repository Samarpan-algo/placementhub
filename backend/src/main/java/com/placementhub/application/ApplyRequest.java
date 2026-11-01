package com.placementhub.application;

import jakarta.validation.constraints.NotNull;

/** Body for POST /api/students/me/applications. The student comes from the current user. */
public record ApplyRequest(@NotNull Long driveId) {
}
