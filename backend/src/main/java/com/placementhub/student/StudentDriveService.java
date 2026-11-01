package com.placementhub.student;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.placementhub.application.ApplicationRepository;
import com.placementhub.application.ApplicationStatus;
import com.placementhub.application.DriveApplicationStatus;
import com.placementhub.application.EligibilityResult;
import com.placementhub.application.EligibilityService;
import com.placementhub.common.PageResponse;
import com.placementhub.drive.Drive;
import com.placementhub.drive.DriveMapper;
import com.placementhub.drive.DriveRepository;
import com.placementhub.drive.DriveStatus;

/**
 * OPEN drives as seen by the current student, each with an eligibility result and the
 * student's application status (null if not applied).
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
	private final ApplicationRepository applicationRepository;
	private final Clock clock;

	public StudentDriveService(CurrentStudentResolver currentStudentResolver, DriveRepository driveRepository,
			DriveMapper driveMapper, EligibilityService eligibilityService,
			ApplicationRepository applicationRepository, Clock clock) {
		this.currentStudentResolver = currentStudentResolver;
		this.driveRepository = driveRepository;
		this.driveMapper = driveMapper;
		this.eligibilityService = eligibilityService;
		this.applicationRepository = applicationRepository;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public PageResponse<StudentDriveResponse> browse(boolean eligibleOnly, Pageable pageable) {
		StudentProfile student = currentStudentResolver.requireProfile();
		Instant now = Instant.now(clock);

		// 2 queries: drives JOIN companies, then one batch for all branches
		List<Drive> drives = driveRepository.findByStatusOrderByRegistrationDeadlineAsc(DriveStatus.OPEN);
		// +1 query: the student's application status on all of these drives at once
		Map<Long, ApplicationStatus> statusByDriveId = applicationStatuses(student, drives);

		List<StudentDriveResponse> items = drives.stream()
				.map(drive -> {
					EligibilityResult result = eligibilityService.evaluate(student, drive, now);
					return new StudentDriveResponse(driveMapper.toResponse(drive), result.eligible(), result.reasons(),
							statusByDriveId.get(drive.getId()));
				})
				.filter(item -> !eligibleOnly || item.eligible())
				.toList();

		return PageResponse.from(pageOf(items, pageable));
	}

	private Map<Long, ApplicationStatus> applicationStatuses(StudentProfile student, List<Drive> drives) {
		if (drives.isEmpty()) {
			return Map.of(); // skip the query: "IN ()" would be pointless
		}
		List<Long> driveIds = drives.stream().map(Drive::getId).toList();
		return applicationRepository.findStatuses(student.getId(), driveIds).stream()
				.collect(Collectors.toMap(DriveApplicationStatus::driveId, DriveApplicationStatus::status));
	}

	private static <T> PageImpl<T> pageOf(List<T> all, Pageable pageable) {
		int from = (int) Math.min(pageable.getOffset(), all.size());
		int to = Math.min(from + pageable.getPageSize(), all.size());
		return new PageImpl<>(all.subList(from, to), pageable, all.size());
	}

}
