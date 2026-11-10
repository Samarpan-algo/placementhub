package com.placementhub.resume;

/** A short-lived link to a resume in the private bucket. */
public record ResumeLinkResponse(String url, long expiresInSeconds) {
}
