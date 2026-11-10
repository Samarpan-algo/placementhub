package com.placementhub.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.placementhub.common.Branch;
import com.placementhub.round.RoundOutcome;

class ApplicantCsvTest {

	private static ApplicantResponse applicant(String name, String phone, ApplicantResponse.LatestRound latest) {
		return new ApplicantResponse(11L, ApplicationStatus.SHORTLISTED, null,
				Instant.parse("2026-10-20T09:30:00Z"), Instant.parse("2026-10-21T09:30:00Z"), 1L, name, "23CSE001",
				"riya@student.dev", Branch.CSE, new BigDecimal("8.50"), 0, 2027, phone, false,
				new ApplicantResponse.Progress(3, latest));
	}

	@Test
	void startsWithABomAndTheHeaderRow() {
		String csv = ApplicantCsv.write(List.of());

		assertThat(csv).startsWith("﻿Application ID,Roll No,Name,Email,Branch,CGPA,");
		assertThat(csv.lines()).hasSize(1);
	}

	@Test
	void oneRowPerApplicantWithItsLatestRound() {
		String csv = ApplicantCsv.write(List.of(applicant("Riya", "9876543210",
				new ApplicantResponse.LatestRound(2, "Technical", RoundOutcome.CLEARED, null))));

		assertThat(csv.split("\r\n")[1]).isEqualTo("11,23CSE001,Riya,riya@student.dev,CSE,8.50,0,2027,9876543210,"
				+ "SHORTLISTED,,Round 2: Technical,CLEARED,3,2026-10-20T09:30:00Z");
	}

	@Test
	void noRoundResultYetLeavesTheRoundCellsEmpty() {
		String row = ApplicantCsv.write(List.of(applicant("Riya", null, null))).split("\r\n")[1];

		assertThat(row).contains(",SHORTLISTED,,,,3,");
	}

	@Test
	void aFormulaInTheNameIsExportedAsText() {
		String row = ApplicantCsv.write(List.of(applicant("=HYPERLINK(\"http://evil.site\",\"x\")", "+919876543210",
				null))).split("\r\n")[1];

		assertThat(row).contains(",\"'=HYPERLINK(\"\"http://evil.site\"\",\"\"x\"\")\",");
		assertThat(row).contains(",'+919876543210,");
	}

	@Test
	void fileNameNamesTheDrive() {
		assertThat(ApplicantCsv.fileName(7L)).isEqualTo("drive-7-applicants.csv");
	}

}
