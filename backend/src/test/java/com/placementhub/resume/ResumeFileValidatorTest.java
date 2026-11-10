package com.placementhub.resume;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockMultipartFile;

import com.placementhub.common.BadRequestException;

class ResumeFileValidatorTest {

	private static final byte[] PDF = "%PDF-1.7 content".getBytes(StandardCharsets.US_ASCII);

	private static MockMultipartFile file(String contentType, byte[] bytes) {
		return new MockMultipartFile("file", "resume.pdf", contentType, bytes);
	}

	@Test
	void aRealPdfIsAcceptedAndItsBytesReturned() {
		assertThat(ResumeFileValidator.validate(file("application/pdf", PDF))).isEqualTo(PDF);
	}

	@ParameterizedTest
	@ValueSource(strings = { "APPLICATION/PDF", "application/pdf; name=cv.pdf" })
	void contentTypeCaseAndParametersDontMatter(String contentType) {
		assertThat(ResumeFileValidator.validate(file(contentType, PDF))).isEqualTo(PDF);
	}

	@Test
	void aMissingFileIsRejected() {
		assertThatThrownBy(() -> ResumeFileValidator.validate(null))
				.isInstanceOf(BadRequestException.class)
				.hasMessage("The file is empty. Choose a PDF file to upload");
	}

	@Test
	void anEmptyFileIsRejected() {
		assertThatThrownBy(() -> ResumeFileValidator.validate(file("application/pdf", new byte[0])))
				.isInstanceOf(BadRequestException.class)
				.hasMessage("The file is empty. Choose a PDF file to upload");
	}

	@ParameterizedTest
	@ValueSource(strings = { "image/png", "text/plain", "application/octet-stream", "*/*", "not a type" })
	void aPdfWithTheWrongContentTypeIsRejected(String contentType) {
		assertThatThrownBy(() -> ResumeFileValidator.validate(file(contentType, PDF)))
				.isInstanceOf(BadRequestException.class)
				.hasMessage("Only PDF files are allowed");
	}

	@Test
	void noContentTypeIsRejected() {
		assertThatThrownBy(() -> ResumeFileValidator.validate(file(null, PDF)))
				.isInstanceOf(BadRequestException.class)
				.hasMessage("Only PDF files are allowed");
	}

	@ParameterizedTest
	@ValueSource(strings = { "<html>not a pdf</html>", "%PD", " %PDF-1.7" })
	void pdfContentTypeWithOtherBytesIsRejected(String content) {
		byte[] bytes = content.getBytes(StandardCharsets.US_ASCII);
		assertThatThrownBy(() -> ResumeFileValidator.validate(file("application/pdf", bytes)))
				.isInstanceOf(BadRequestException.class)
				.hasMessage("This file is not a valid PDF");
	}

	@Test
	void exactly2MbIsAccepted() {
		byte[] bytes = pdfOfSize((int) ResumeFileValidator.MAX_SIZE.toBytes());
		assertThat(ResumeFileValidator.validate(file("application/pdf", bytes))).hasSize(2 * 1024 * 1024);
	}

	@Test
	void oneByteOver2MbIsRejected() {
		byte[] bytes = pdfOfSize((int) ResumeFileValidator.MAX_SIZE.toBytes() + 1);
		assertThatThrownBy(() -> ResumeFileValidator.validate(file("application/pdf", bytes)))
				.isInstanceOf(BadRequestException.class)
				.hasMessage("The file is too large. The maximum size is 2 MB");
	}

	private static byte[] pdfOfSize(int size) {
		return Arrays.copyOf(PDF, size);
	}

}
