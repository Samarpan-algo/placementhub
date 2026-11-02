package com.placementhub.application;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
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
import com.placementhub.policy.PlacementPolicy;
import com.placementhub.policy.PlacementPolicyRepository;
import com.placementhub.policy.PolicyEngine;
import com.placementhub.policy.PolicyEvaluation;
import com.placementhub.round.DriveRoundRepository;
import com.placementhub.round.RoundResultRepository;
import com.placementhub.student.CurrentStudentResolver;
import com.placementhub.student.StudentProfile;

/**
 * The current student's applications: apply, withdraw, view.
 * Apply rules (in order): drive exists and is published, no earlier application,
 * eligible and not blocked by a placement policy (one 422 with all reasons), then insert,
 * with the DB UNIQUE constraint as the race guard.
 * Apply and withdraw lock the student's row first (see StudentProfileRepository), and every
 * status change goes through StatusChanges, which writes the audit row.
 */
@Service
public class ApplicationService {

	static final String UNIQUE_DRIVE_STUDENT = "uq_applications_drive_student";
	static final String ALREADY_APPLIED = "You have already applied to this drive";
	static final String WITHDRAWAL_IS_FINAL = "You withdrew from this drive; withdrawal is final";
	static final String NOT_ELIGIBLE = "You are not eligible for this drive";
	static final String BLOCKED_BY_POLICY = "This application is blocked by a placement policy";

	/** Statuses a student may still withdraw from. */
	static final Set<ApplicationStatus> WITHDRAWABLE = EnumSet.of(ApplicationStatus.APPLIED,
			ApplicationStatus.SHORTLISTED);

	private final CurrentStudentResolver currentStudentResolver;
	private final DriveRepository driveRepository;
	private final ApplicationRepository applicationRepository;
	private final EligibilityService eligibilityService;
	private final PlacementPolicyRepository policyRepository;
	private final PolicyEngine policyEngine;
	private final ApplicationMapper applicationMapper;
	private final StatusChanges statusChanges;
	private final DriveRoundRepository roundRepository;
	private final RoundResultRepository resultRepository;
	private final Clock clock;

	public ApplicationService(CurrentStudentResolver currentStudentResolver, DriveRepository driveRepository,
			ApplicationRepository applicationRepository, EligibilityService eligibilityService,
			PlacementPolicyRepository policyRepository, PolicyEngine policyEngine,
			ApplicationMapper applicationMapper, StatusChanges statusChanges, DriveRoundRepository roundRepository,
			RoundResultRepository resultRepository, Clock clock) {
		this.currentStudentResolver = currentStudentResolver;
		this.driveRepository = driveRepository;
		this.applicationRepository = applicationRepository;
		this.eligibilityService = eligibilityService;
		this.policyRepository = policyRepository;
		this.policyEngine = policyEngine;
		this.applicationMapper = applicationMapper;
		this.statusChanges = statusChanges;
		this.roundRepository = roundRepository;
		this.resultRepository = resultRepository;
		this.clock = clock;
	}

	// ---------- apply ----------

	@Transactional
	public ApplicationResponse apply(ApplyRequest request) {
		// Locked first: an admin selecting this student right now can't slip an offer past the policy check
		StudentProfile student = currentStudentResolver.requireProfileForUpdate();

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

		// Eligibility and placement policies together: one 422 with every reason, the same list browse shows
		EligibilityResult eligibility = eligibilityService.evaluate(student, drive, Instant.now(clock));
		PolicyEvaluation policies = evaluatePolicies(student, drive);
		if (!eligibility.eligible() || !policies.allowed()) {
			List<String> reasons = new ArrayList<>(eligibility.reasons());
			reasons.addAll(policies.reasons());
			throw new UnprocessableEntityException(eligibility.eligible() ? BLOCKED_BY_POLICY : NOT_ELIGIBLE,
					reasons);
		}

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
		statusChanges.created(saved, StatusChangeTrigger.APPLY);
		return applicationMapper.toResponse(saved);
	}

	/** Internship drives, or no active policy: no queries at all. Otherwise +2 queries (policies, offers). */
	private PolicyEvaluation evaluatePolicies(StudentProfile student, Drive drive) {
		if (!policyEngine.appliesTo(drive)) {
			return PolicyEvaluation.ALLOWED;
		}
		List<PlacementPolicy> active = policyRepository.findByActiveTrue();
		if (active.isEmpty()) {
			return PolicyEvaluation.ALLOWED;
		}
		return policyEngine.evaluate(active, drive, applicationRepository.findSelectedOffers(student.getId()));
	}

	// ---------- withdraw ----------

	@Transactional
	public ApplicationResponse withdraw(Long applicationId) {
		// Locked first: a selection elsewhere may be turning this application INELIGIBLE right now
		StudentProfile student = currentStudentResolver.requireProfileForUpdate();
		Application application = findOwnOrThrow(applicationId, student);
		checkCanWithdraw(application, Instant.now(clock));
		statusChanges.change(application, ApplicationStatus.WITHDRAWN, null, StatusChangeTrigger.WITHDRAW);
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

	/** Round-by-round tracking: every round of the drive, with this student's result and remarks (+2 queries). */
	@Transactional(readOnly = true)
	public ApplicationDetailResponse getMine(Long applicationId) {
		Application application = findOwnOrThrow(applicationId, currentStudentResolver.requireProfile());
		return applicationMapper.toDetailResponse(application,
				roundRepository.findByDriveIdOrderByRoundNoAsc(application.getDrive().getId()),
				resultRepository.findByApplicationId(application.getId()));
	}

	@Transactional(readOnly = true)
	public PageResponse<ApplicationResponse> listMine(Pageable pageable) {
		StudentProfile student = currentStudentResolver.requireProfile();
		return PageResponse.from(
				applicationRepository.findByStudentId(student.getId(), pageable).map(applicationMapper::toResponse));
	}

	/** Someone else's application gives the same 404 as a missing one, so its existence is not revealed. */
	private Application findOwnOrThrow(Long applicationId, StudentProfile student) {
		return applicationRepository.findByIdAndStudentId(applicationId, student.getId())
				.orElseThrow(() -> ResourceNotFoundException.of("Application", applicationId));
	}

}
