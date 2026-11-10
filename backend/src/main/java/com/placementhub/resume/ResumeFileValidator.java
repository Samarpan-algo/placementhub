package com.placementhub.resume;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.util.unit.DataSize;
import org.springframework.web.multipart.MultipartFile;

import com.placementhub.common.BadRequestException;

/**
 * The rules for an uploaded resume. The browser's content type can say anything, so the first bytes are
 * checked as well. Spring also enforces the size limit (spring.servlet.multipart.max-file-size) before the
 * controller runs; the check here keeps the rule true however the service is called.
 */
final class ResumeFileValidator {

	static final DataSize MAX_SIZE = DataSize.ofMegabytes(2);

	private static final byte[] PDF_MAGIC = "%PDF".getBytes(StandardCharsets.US_ASCII);

	private ResumeFileValidator() {
	}

	/** @return the file's bytes when it is a valid resume */
	static byte[] validate(MultipartFile file) {
		if (file == null || file.isEmpty()) {
			throw new BadRequestException("The file is empty. Choose a PDF file to upload");
		}
		if (file.getSize() > MAX_SIZE.toBytes()) {
			throw new BadRequestException("The file is too large. The maximum size is 2 MB");
		}
		if (!isPdfContentType(file.getContentType())) {
			throw new BadRequestException("Only PDF files are allowed");
		}
		byte[] bytes = readBytes(file);
		if (!startsWithPdfMagic(bytes)) {
			throw new BadRequestException("This file is not a valid PDF");
		}
		return bytes;
	}

	private static boolean isPdfContentType(String contentType) {
		if (contentType == null) {
			return false;
		}
		try {
			// equalsTypeAndSubtype ignores parameters such as "; charset=..." and upper/lower case
			return MediaType.APPLICATION_PDF.equalsTypeAndSubtype(MediaType.parseMediaType(contentType));
		} catch (InvalidMediaTypeException ex) {
			return false;
		}
	}

	private static boolean startsWithPdfMagic(byte[] bytes) {
		return bytes.length >= PDF_MAGIC.length
				&& Arrays.equals(Arrays.copyOf(bytes, PDF_MAGIC.length), PDF_MAGIC);
	}

	private static byte[] readBytes(MultipartFile file) {
		try {
			return file.getBytes();
		} catch (IOException ex) {
			throw new UncheckedIOException("Could not read the uploaded file", ex);
		}
	}

}
