package com.placementhub.application;

import java.time.Clock;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.placementhub.common.ConflictException;
import com.placementhub.common.ConstraintViolations;
import com.placementhub.common.PageResponse;
import com.placementhub.common.ResourceNotFoundException;
import com.placementhub.common.StudentTimeFormat;
import com.placementhub.common.UnprocessableEntityException;
import com.placementhub.drive.Drive;
import com.placementhub.drive.DriveRepository;
import com.placementhub.drive.DriveStatus;
import com.placementhub.student.CurrentStudentResolver;
import com.placementhub.student.StudentProfile;

/**
 * The current student's applications: apply, withdraw, view.
 * Apply rules (in order): drive exists and is published, no earlier application,
 * eligible, [policy engine, Milestone 6], then insert, with the DB UNIQUE constraint as the race guard.
 */
@Service
public class ApplicationService {

	static final String UNIQUE_DRIVE_STUDENT = "uq_applications_drive_student";
	static final String ALREADY_APPLIED = "You have already applied to this drive";
	static final String WITHDRAWAL_IS_FINAL = "You withdrew from this drive; withdrawal is final";

	/** Statuses a student may still withdraw from. */
	static final Set<ApplicationStatus> WITHDRAWABLE = EnumSet.of(ApplicationStatus.APPLIED,
			ApplicationStatus.SHORTLISTED);

	private final CurrentStudentResolver currentStudentResolver;
	private final DriveRepository driveRepository;
	private final ApplicationRepository applicationRepository;
	private final EligibilityService eligibilityService;
	private final ApplicationMapper applicationMapper;
	private final Clock clock;

	public ApplicationService(CurrentStudentResolver currentStudentResolver, DriveRepository driveRepository,
			ApplicationRepository applicationRepository, EligibilityService eligibilityService,
			ApplicationMapper applicationMapper, Clock clock) {
		this.currentStudentResolver = currentStudentResolver;
		this.driveRepository = driveRepository;
		this.applicationRepository = applicationRepository;
		this.eligibilityService = eligibilityService;
		this.applicationMapper = applicationMapper;
		this.clock = clock;
	}

	// ---------- apply ----------

	@Transactional
	public ApplicationResponse apply(ApplyRequest request) {
		StudentProfile student = currentStudentResolver.requireProfile();

		// DRAFT drives are unpublished: students must not learn they exist
		Drive drive = driveRepository.findById(request.driveId())
				.filter(d -> d.getStatus() != DriveStatus.DRAFT)
				.orElseThrow(() -> ResourceNotFoundException.of("Drive", request.driveId()));

		// Earlier application? Checked before eligibility so the message makes sense.
		applicationRepository.findByDriveIdAndStudentId(request.driveId(), student.getId())
				.ifPresent(existing -> {
					throw new ConflictException(existing.getStatus() == ApplicationStatus.WITHDRAWN
							? WITHDRAWAL_IS_FINAL
							: ALREADY_APPLIED);
				});

		EligibilityResult eligibility = eligibilityService.evaluate(student, drive, Instant.now(clock));
		if (!eligibility.eligible()) {
			throw new UnprocessableEntityException("You are not eligible for this drive", eligibility.reasons());
		}

		// ===== POLICY ENGINE HOOK (Milestone 6) =====
		// Load the student's offers and run every active PolicyRule (ONE_OFFER, DREAM_OFFER, MAX_OFFERS) here.
		// A blocked result must throw before the insert below, with the rule's reason.
		// ============================================

		Application saved;
		try {
			// saveAndFlush runs the INSERT now, so a duplicate is caught here and not at commit time
			saved = applicationRepository.saveAndFlush(new Application(drive, student));
		} catch (DataIntegrityViolationException e) {
			// Two concurrent applies both passed the check above; the DB UNIQUE constraint stopped the second
			if (ConstraintViolations.isViolationOf(e, UNIQUE_DRIVE_STUDENT)) {
				throw new ConflictException(ALREADY_APPLIED);
			}
			throw e;
		}
		return applicationMapper.toResponse(saved);
	}

	// ---------- withdraw ----------

	@Transactional
	public ApplicationResponse withdraw(Long applicationId) {
		Application application = findOwnOrThrow(applicationId);
		checkCanWithdraw(application, Instant.now(clock));
		application.setStatus(ApplicationStatus.WITHDRAWN);
		// TODO Milestone 7: write an audit log entry for this status change
		applicationRepository.flush();
		return applicationMapper.toResponse(application);
	}

	private void checkCanWithdraw(Application application, Instant now) {
		ApplicationStatus status = application.getStatus();
		if (status == ApplicationStatus.WITHDRAWN) {
			throw new ConflictException("You have already withdrawn this application");
		}
		if (!WITHDRAWABLE.contains(status)) {
			throw new ConflictException("Cannot withdraw a " + status
					+ " application. Only APPLIED or SHORTLISTED applications can be withdrawn");
		}
		Drive drive = application.getDrive();
		if (drive.getStatus() != DriveStatus.OPEN) {
			throw new ConflictException(
					"Withdrawal is not allowed: this drive is no longer open (status: " + drive.getStatus() + ")");
		}
		if (!now.isBefore(drive.getRegistrationDeadline())) {
			throw new ConflictException("Withdrawal closed on "
					+ StudentTimeFormat.format(drive.getRegistrationDeadline()) + " (the registration deadline)");
		}
	}

	// ---------- read ----------

	@Transactional(readOnly = true)
	public ApplicationResponse getMine(Long applicationId) {
		return applicationMapper.toResponse(findOwnOrThrow(applicationId));
	}

	@Transactional(readOnly = true)
	public PageResponse<ApplicationResponse> listMine(Pageable pageable) {
		StudentProfile student = currentStudentResolver.requireProfile();
		return PageResponse.from(
				applicationRepository.findByStudentId(student.getId(), pageable).map(applicationMapper::toResponse));
	}

	/** Someone else's application gives the same 404 as a missing one, so its existence is not revealed. */
	private Application findOwnOrThrow(Long applicationId) {
		StudentProfile student = currentStudentResolver.requireProfile();
		return applicationRepository.findByIdAndStudentId(applicationId, student.getId())
				.orElseThrow(() -> ResourceNotFoundException.of("Application", applicationId));
	}

}
