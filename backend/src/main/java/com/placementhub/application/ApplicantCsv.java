package com.placementhub.application;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

import com.placementhub.common.CsvWriter;

/** The applicants CSV: a header row, then one row per applicant. Pure: easy to test without Spring. */
public final class ApplicantCsv {

	static final List<String> HEADER = List.of("Application ID", "Roll No", "Name", "Email", "Branch", "CGPA",
			"Active Backlogs", "Graduation Year", "Phone", "Status", "Status Reason", "Latest Round",
			"Latest Result", "Total Rounds", "Applied At");

	private ApplicantCsv() {
	}

	public static String write(List<ApplicantResponse> applicants) {
		StringBuilder csv = new StringBuilder(CsvWriter.UTF8_BOM).append(CsvWriter.row(HEADER));
		applicants.forEach(a -> csv.append(CsvWriter.row(cells(a))));
		return csv.toString();
	}

	static String fileName(Long driveId) {
		return "drive-" + driveId + "-applicants.csv";
	}

	private static List<String> cells(ApplicantResponse a) {
		ApplicantResponse.LatestRound latest = a.progress().latestRound();
		return Arrays.asList( // Arrays.asList, not List.of: some cells are null
				String.valueOf(a.applicationId()),
				a.rollNo(),
				a.fullName(),
				a.email(),
				a.branch().name(),
				plain(a.cgpa()),
				String.valueOf(a.activeBacklogs()),
				String.valueOf(a.graduationYear()),
				a.phone(),
				a.status().name(),
				a.statusReason(),
				latest == null ? null : "Round " + latest.roundNo() + ": " + latest.name(),
				latest == null ? null : latest.result().name(),
				String.valueOf(a.progress().totalRounds()),
				a.appliedAt() == null ? null : a.appliedAt().toString());
	}

	private static String plain(BigDecimal value) {
		return value == null ? null : value.toPlainString();
	}

}
