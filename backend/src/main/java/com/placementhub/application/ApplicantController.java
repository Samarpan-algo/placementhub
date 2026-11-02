package com.placementhub.application;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.placementhub.common.Branch;
import com.placementhub.common.PageResponse;

/** Admin: a drive's applicants (TPO_ADMIN via /api/drives/** in SecurityConfig). */
@RestController
@RequestMapping("/api/drives/{driveId}/applications")
public class ApplicantController {

	private final ApplicantService applicantService;

	public ApplicantController(ApplicantService applicantService) {
		this.applicantService = applicantService;
	}

	@GetMapping
	public PageResponse<ApplicantResponse> list(
			@PathVariable Long driveId,
			@RequestParam(required = false) ApplicationStatus status,
			@RequestParam(required = false) Branch branch,
			@RequestParam(required = false) BigDecimal minCgpa,
			@PageableDefault(size = 20, sort = "student.rollNo", direction = Sort.Direction.ASC) Pageable pageable) {
		return applicantService.list(driveId, new ApplicantFilter(status, branch, minCgpa), pageable);
	}

	/**
	 * The same filters, every row, as a CSV download. No "produces" on purpose: errors (400/404) are still
	 * returned as the standard JSON error body.
	 */
	@GetMapping("/export")
	public ResponseEntity<byte[]> export(
			@PathVariable Long driveId,
			@RequestParam(required = false) ApplicationStatus status,
			@RequestParam(required = false) Branch branch,
			@RequestParam(required = false) BigDecimal minCgpa) {
		String csv = ApplicantCsv.write(applicantService.listAll(driveId, new ApplicantFilter(status, branch, minCgpa)));
		return ResponseEntity.ok()
				.contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
				.header(HttpHeaders.CONTENT_DISPOSITION,
						ContentDisposition.attachment().filename(ApplicantCsv.fileName(driveId)).build().toString())
				.body(csv.getBytes(StandardCharsets.UTF_8));
	}

}
