package com.placementhub.application;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.placementhub.common.BadRequestException;
import com.placementhub.common.PageResponse;
import com.placementhub.common.ResourceNotFoundException;
import com.placementhub.drive.DriveRepository;
import com.placementhub.round.DriveRoundRepository;
import com.placementhub.round.RoundResult;
import com.placementhub.round.RoundResultRepository;
import com.placementhub.student.StudentProfile;

/**
 * Admin: a drive's applicants with student details and round progress, as a page (JSON) or all rows (CSV).
 * A fixed number of queries however many rows: the drive exists, the applications with student and user
 * JOINed (+ a count for a page), the drive's round count, and the rows' round results in one IN query.
 */
@Service
public class ApplicantService {

	/** The CSV export is sorted by roll number, like the default page order. */
	static final Sort EXPORT_SORT = Sort.by("student.rollNo");

	private final DriveRepository driveRepository;
	private final ApplicationRepository applicationRepository;
	private final DriveRoundRepository roundRepository;
	private final RoundResultRepository resultRepository;

	public ApplicantService(DriveRepository driveRepository, ApplicationRepository applicationRepository,
			DriveRoundRepository roundRepository, RoundResultRepository resultRepository) {
		this.driveRepository = driveRepository;
		this.applicationRepository = applicationRepository;
		this.roundRepository = roundRepository;
		this.resultRepository = resultRepository;
	}

	@Transactional(readOnly = true)
	public PageResponse<ApplicantResponse> list(Long driveId, ApplicantFilter filter, Pageable pageable) {
		Page<Application> page = search(driveId, filter, pageable);
		return new PageResponse<>(toResponses(driveId, page.getContent()), page.getNumber(), page.getSize(),
				page.getTotalElements(), page.getTotalPages());
	}

	/** Every matching applicant, no paging (for the CSV export). */
	@Transactional(readOnly = true)
	public List<ApplicantResponse> listAll(Long driveId, ApplicantFilter filter) {
		// Unpaged: Spring Data runs no count query
		return toResponses(driveId, search(driveId, filter, Pageable.unpaged(EXPORT_SORT)).getContent());
	}

	private Page<Application> search(Long driveId, ApplicantFilter filter, Pageable pageable) {
		checkMinCgpa(filter.minCgpa());
		if (!driveRepository.existsById(driveId)) {
			throw ResourceNotFoundException.of("Drive", driveId);
		}
		return applicationRepository.searchApplicants(driveId, filter.status(), filter.branch(), filter.minCgpa(),
				pageable);
	}

	private List<ApplicantResponse> toResponses(Long driveId, List<Application> applications) {
		if (applications.isEmpty()) {
			return List.of();
		}
		int totalRounds = (int) roundRepository.countByDriveId(driveId);
		Map<Long, RoundResult> latest = totalRounds == 0
				? Map.of()
				: latestResults(applications.stream().map(Application::getId).toList());
		return applications.stream()
				.map(a -> toResponse(a,
						new ApplicantResponse.Progress(totalRounds, latestRound(latest.get(a.getId())))))
				.toList();
	}

	/** One query for all rows; then, per application, its result in the highest-numbered round. */
	private Map<Long, RoundResult> latestResults(List<Long> applicationIds) {
		return resultRepository.findWithRoundByApplicationIdIn(applicationIds).stream()
				.collect(Collectors.toMap(r -> r.getApplication().getId(), r -> r,
						(a, b) -> a.getRound().getRoundNo() >= b.getRound().getRoundNo() ? a : b));
	}

	private static ApplicantResponse.LatestRound latestRound(RoundResult result) {
		if (result == null) {
			return null;
		}
		return new ApplicantResponse.LatestRound(result.getRound().getRoundNo(), result.getRound().getName(),
				result.getResult(), result.getRemarks());
	}

	private static ApplicantResponse toResponse(Application application, ApplicantResponse.Progress progress) {
		StudentProfile student = application.getStudent();
		return new ApplicantResponse(
				application.getId(),
				application.getStatus(),
				application.getStatusReason(),
				application.getAppliedAt(),
				application.getUpdatedAt(),
				student.getId(),
				student.getFullName(),
				student.getRollNo(),
				student.getUser().getEmail(),
				student.getBranch(),
				student.getCgpa(),
				student.getActiveBacklogs(),
				student.getGraduationYear(),
				student.getPhone(),
				student.getResumePath() != null,
				progress);
	}

	private static void checkMinCgpa(BigDecimal minCgpa) {
		if (minCgpa != null && (minCgpa.signum() < 0 || minCgpa.compareTo(BigDecimal.TEN) > 0)) {
			throw new BadRequestException("minCgpa must be between 0 and 10");
		}
	}

}
