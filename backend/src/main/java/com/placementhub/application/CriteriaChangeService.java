package com.placementhub.application;

import java.time.Clock;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.placementhub.drive.Drive;
import com.placementhub.student.StudentProfileRepository;

/**
 * Re-checks a drive's pending applications after an admin changed its eligibility criteria.
 * Rules:
 * - Only APPLIED and SHORTLISTED applications are re-checked, with the unchanged EligibilityService.
 * - One that now fails becomes INELIGIBLE with "Drive criteria changed: <every reason>", audited
 *   (trigger CRITERIA_CHANGE). One that still passes is left alone.
 * - One-way only: loosening the criteria never restores an INELIGIBLE application (decisions.md).
 * - Placement policies are NOT re-checked here: selection re-checks them anyway (decision 49).
 */
@Service
public class CriteriaChangeService {

	static final Set<ApplicationStatus> RECHECKED = EnumSet.of(ApplicationStatus.APPLIED,
			ApplicationStatus.SHORTLISTED);

	static final String REASON_PREFIX = "Drive criteria changed: ";

	private final ApplicationRepository applicationRepository;
	private final StudentProfileRepository studentProfileRepository;
	private final EligibilityService eligibilityService;
	private final StatusChanges statusChanges;
	private final Clock clock;

	public CriteriaChangeService(ApplicationRepository applicationRepository,
			StudentProfileRepository studentProfileRepository, EligibilityService eligibilityService,
			StatusChanges statusChanges, Clock clock) {
		this.applicationRepository = applicationRepository;
		this.studentProfileRepository = studentProfileRepository;
		this.eligibilityService = eligibilityService;
		this.statusChanges = statusChanges;
		this.clock = clock;
	}

	/**
	 * The caller must already hold the drive's FOR UPDATE lock and have applied the new criteria to `drive`.
	 * The drive must be OPEN with a future deadline (DriveService checks this first), so EligibilityService's
	 * status and deadline rules pass and only the criteria decide.
	 * @return how many applications became INELIGIBLE
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public int reevaluate(Drive drive) {
		// Lock order (decision 50): the drive (held by the caller), then the students by id, then load fresh
		List<Long> studentIds = applicationRepository.findStudentIdsByDriveIdAndStatusIn(drive.getId(), RECHECKED);
		if (studentIds.isEmpty()) {
			return 0;
		}
		studentProfileRepository.findAllByIdForUpdate(studentIds);
		List<Application> pending = applicationRepository.findWithStudentByDriveIdAndStatusIn(drive.getId(),
				RECHECKED);

		Instant now = Instant.now(clock);
		int madeIneligible = 0;
		for (Application application : pending) {
			EligibilityResult result = eligibilityService.evaluate(application.getStudent(), drive, now);
			if (!result.eligible()) {
				statusChanges.change(application, ApplicationStatus.INELIGIBLE,
						REASON_PREFIX + String.join("; ", result.reasons()), StatusChangeTrigger.CRITERIA_CHANGE);
				madeIneligible++;
			}
		}
		return madeIneligible;
	}

}
