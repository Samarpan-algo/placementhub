package com.placementhub.student;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.placementhub.application.EligibilityResult;
import com.placementhub.application.EligibilityService;
import com.placementhub.common.PageResponse;
import com.placementhub.drive.DriveMapper;
import com.placementhub.drive.DriveRepository;
import com.placementhub.drive.DriveStatus;

/**
 * OPEN drives as seen by the current student, each with an eligibility result.
 * Eligibility is computed in Java (EligibilityService is the single source of truth), so
 * filtering and paging happen in memory over all OPEN drives. Fine for tens or a few hundred
 * OPEN drives; see decisions.md.
 */
@Service
public class StudentDriveService {

	private final CurrentStudentResolver currentStudentResolver;
	private final DriveRepository driveRepository;
	private final DriveMapper driveMapper;
	private final EligibilityService eligibilityService;
	private final Clock clock;

	public StudentDriveService(CurrentStudentResolver currentStudentResolver, DriveRepository driveRepository,
			DriveMapper driveMapper, EligibilityService eligibilityService, Clock clock) {
		this.currentStudentResolver = currentStudentResolver;
		this.driveRepository = driveRepository;
		this.driveMapper = driveMapper;
		this.eligibilityService = eligibilityService;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public PageResponse<StudentDriveResponse> browse(boolean eligibleOnly, Pageable pageable) {
		StudentProfile student = currentStudentResolver.requireProfile();
		Instant now = Instant.now(clock);

		// 2 queries in total: drives JOIN companies, then one batch for all branches
		List<StudentDriveResponse> items = driveRepository.findByStatusOrderByRegistrationDeadlineAsc(DriveStatus.OPEN)
				.stream()
				.map(drive -> {
					EligibilityResult result = eligibilityService.evaluate(student, drive, now);
					return new StudentDriveResponse(driveMapper.toResponse(drive), result.eligible(), result.reasons());
				})
				.filter(item -> !eligibleOnly || item.eligible())
				.toList();

		return PageResponse.from(pageOf(items, pageable));
	}

	private static <T> PageImpl<T> pageOf(List<T> all, Pageable pageable) {
		int from = (int) Math.min(pageable.getOffset(), all.size());
		int to = Math.min(from + pageable.getPageSize(), all.size());
		return new PageImpl<>(all.subList(from, to), pageable, all.size());
	}

}
