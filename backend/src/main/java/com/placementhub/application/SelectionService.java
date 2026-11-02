package com.placementhub.application;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.placementhub.common.BatchValidation;
import com.placementhub.common.ConflictException;
import com.placementhub.common.ResourceNotFoundException;
import com.placementhub.drive.Drive;
import com.placementhub.drive.DriveRepository;
import com.placementhub.drive.DriveStatus;
import com.placementhub.policy.Offer;
import com.placementhub.policy.PlacementPolicy;
import com.placementhub.policy.PlacementPolicyRepository;
import com.placementhub.policy.PolicyEngine;
import com.placementhub.policy.PolicyEvaluation;
import com.placementhub.round.DriveRound;
import com.placementhub.round.DriveRoundRepository;
import com.placementhub.round.RoundResultRepository;
import com.placementhub.student.StudentProfile;
import com.placementhub.student.StudentProfileRepository;

/**
 * Final outcomes of a drive: selecting applications (offers), and rejecting what is left when the drive completes.
 * <p>
 * Selection rules, all checked for every application before anything changes (all or nothing):
 * the drive is CLOSED; each application belongs to it and appears once; it is APPLIED or SHORTLISTED
 * (INELIGIBLE gives its reason); if the drive has rounds, it CLEARED the final one; and no active placement
 * policy blocks it given the student's current offers.
 * <p>
 * After each new FULL_TIME offer, the student's other pending FULL_TIME applications are re-checked
 * against the policies with the new offer included; any now blocked become INELIGIBLE (decision #45).
 * Students are locked first, so two selections for one student run one after the other.
 */
@Service
public class SelectionService {

	static final Set<ApplicationStatus> SELECTABLE = EnumSet.of(ApplicationStatus.APPLIED,
			ApplicationStatus.SHORTLISTED);
	static final String COMPLETED_WITHOUT_SELECTION = "Drive completed without selection";

	private final DriveRepository driveRepository;
	private final ApplicationRepository applicationRepository;
	private final StudentProfileRepository studentProfileRepository;
	private final DriveRoundRepository roundRepository;
	private final RoundResultRepository resultRepository;
	private final PlacementPolicyRepository policyRepository;
	private final PolicyEngine policyEngine;
	private final StatusChanges statusChanges;

	public SelectionService(DriveRepository driveRepository, ApplicationRepository applicationRepository,
			StudentProfileRepository studentProfileRepository, DriveRoundRepository roundRepository,
			RoundResultRepository resultRepository, PlacementPolicyRepository policyRepository,
			PolicyEngine policyEngine, StatusChanges statusChanges) {
		this.driveRepository = driveRepository;
		this.applicationRepository = applicationRepository;
		this.studentProfileRepository = studentProfileRepository;
		this.roundRepository = roundRepository;
		this.resultRepository = resultRepository;
		this.policyRepository = policyRepository;
		this.policyEngine = policyEngine;
		this.statusChanges = statusChanges;
	}

	// ---------- select ----------

	@Transactional
	public SelectionResponse select(Long driveId, SelectionRequest request) {
		// 1. Lock order: the drive, then its students, then load the applications fresh
		Drive drive = driveRepository.findByIdForUpdate(driveId)
				.orElseThrow(() -> ResourceNotFoundException.of("Drive", driveId));
		if (drive.getStatus() != DriveStatus.CLOSED) {
			throw new ConflictException("Selections can be made only while the drive is CLOSED (status: "
					+ drive.getStatus() + ")");
		}
		List<Long> ids = request.applicationIds().stream().distinct().toList();
		lockStudents(applicationRepository.findStudentIdsByIdInAndDriveId(ids, driveId));
		Map<Long, Application> applications = applicationRepository.findByIdInAndDriveId(ids, driveId).stream()
				.collect(Collectors.toMap(Application::getId, Function.identity()));

		// What the checks need, loaded once for the whole batch
		Optional<DriveRound> finalRound = finalRound(driveId);
		Set<Long> clearedFinalRound = finalRound.isEmpty() || applications.isEmpty()
				? Set.of()
				: clearedIn(finalRound.get(), applications.keySet());
		List<PlacementPolicy> policies = policyEngine.appliesTo(drive) ? policyRepository.findByActiveTrue()
				: List.of();
		Map<Long, List<Offer>> offersByStudent = new HashMap<>();
		if (!policies.isEmpty()) {
			for (Application application : applications.values()) {
				Long studentId = application.getStudent().getId();
				offersByStudent.put(studentId, applicationRepository.findSelectedOffers(studentId));
			}
		}

		// 2. Validate every application; nobody is selected if any is invalid
		BatchValidation validation = new BatchValidation();
		Set<Long> seen = new HashSet<>();
		for (Long id : request.applicationIds()) {
			Application application = applications.get(id);
			if (!seen.add(id)) {
				validation.badRequest("Application " + id + " is listed more than once in this batch");
			} else if (application == null) {
				validation.badRequest("Application " + id + " was not found in this drive");
			} else {
				check(application, drive, finalRound, clearedFinalRound, policies,
						offersByStudent.getOrDefault(application.getStudent().getId(), List.of()), validation);
			}
		}
		validation.throwIfAny("Selection was not saved");

		// 3. Select, then re-check each student's other pending applications with the new offer
		List<SelectionResponse.Selected> selected = new ArrayList<>();
		List<SelectionResponse.MadeIneligible> madeIneligible = new ArrayList<>();
		for (Long id : ids) {
			Application application = applications.get(id);
			statusChanges.change(application, ApplicationStatus.SELECTED, null, StatusChangeTrigger.SELECTION);
			StudentProfile student = application.getStudent();
			selected.add(new SelectionResponse.Selected(id, student.getId(), student.getFullName(),
					student.getRollNo(), application.getStatus()));
			if (!policies.isEmpty()) {
				List<Offer> offersNow = new ArrayList<>(offersByStudent.get(student.getId()));
				offersNow.add(new Offer(drive.getCompany().getName(), drive.getCtcLpa(), drive.getJobType()));
				madeIneligible.addAll(reevaluatePending(application, policies, offersNow));
			}
		}
		applicationRepository.flush();
		return new SelectionResponse(driveId, selected, madeIneligible);
	}

	private void check(Application application, Drive drive, Optional<DriveRound> finalRound,
			Set<Long> clearedFinalRound, List<PlacementPolicy> policies, List<Offer> offers,
			BatchValidation validation) {
		Long id = application.getId();
		ApplicationStatus status = application.getStatus();
		if (status == ApplicationStatus.INELIGIBLE) {
			validation.conflict("Application " + id + " is INELIGIBLE and can't be selected: "
					+ application.getStatusReason());
			return;
		}
		if (status == ApplicationStatus.SELECTED) {
			validation.conflict("Application " + id + " is already SELECTED");
			return;
		}
		if (!SELECTABLE.contains(status)) {
			String reason = application.getStatusReason() == null ? "" : " (" + application.getStatusReason() + ")";
			validation.conflict("Application " + id + " is " + status + reason
					+ "; only APPLIED or SHORTLISTED applications can be selected");
			return;
		}
		if (finalRound.isPresent() && !clearedFinalRound.contains(id)) {
			validation.conflict("Application " + id + " has not cleared the final round ("
					+ finalRound.get().label() + ")");
			return;
		}
		// The policy may have changed since the student applied (e.g. switched on later)
		if (!policies.isEmpty()) {
			PolicyEvaluation evaluation = policyEngine.evaluate(policies, drive, offers);
			if (!evaluation.allowed()) {
				validation.conflict("Application " + id + " is blocked by a placement policy: "
						+ String.join("; ", evaluation.reasons()));
			}
		}
	}

	/**
	 * The decision #45 fix. "May this pending application continue?" is the same question as
	 * "could the student apply to that drive now?", so the same PolicyEngine answers it.
	 */
	private List<SelectionResponse.MadeIneligible> reevaluatePending(Application selected,
			List<PlacementPolicy> policies, List<Offer> offersNow) {
		List<SelectionResponse.MadeIneligible> changed = new ArrayList<>();
		StudentProfile student = selected.getStudent();
		for (Application pending : applicationRepository.findPendingFullTimeByStudentId(student.getId(),
				selected.getId())) {
			PolicyEvaluation evaluation = policyEngine.evaluate(policies, pending.getDrive(), offersNow);
			if (evaluation.allowed()) {
				continue; // e.g. DREAM_OFFER: a drive at or above the threshold is still an allowed upgrade
			}
			statusChanges.change(pending, ApplicationStatus.INELIGIBLE, String.join("; ", evaluation.reasons()),
					StatusChangeTrigger.POLICY);
			Drive drive = pending.getDrive();
			changed.add(new SelectionResponse.MadeIneligible(pending.getId(), student.getId(),
					student.getFullName(), drive.getId(), drive.getCompany().getName(), drive.getRoleTitle(),
					pending.getStatusReason()));
		}
		return changed;
	}

	// ---------- complete ----------

	/**
	 * Called by DriveService when a drive becomes COMPLETED, in the same transaction and with the drive
	 * row already locked: every application still APPLIED or SHORTLISTED is REJECTED, audited.
	 */
	@Transactional
	public int rejectPending(Drive drive) {
		lockStudents(applicationRepository.findStudentIdsByDriveIdAndStatusIn(drive.getId(), SELECTABLE));
		List<Application> pending = applicationRepository.findByDriveIdAndStatusIn(drive.getId(), SELECTABLE);
		for (Application application : pending) {
			statusChanges.change(application, ApplicationStatus.REJECTED, COMPLETED_WITHOUT_SELECTION,
					StatusChangeTrigger.DRIVE_COMPLETED);
		}
		return pending.size();
	}

	// ---------- helpers ----------

	/** See StudentProfileRepository: the student row is the lock for that student's applications. */
	private void lockStudents(List<Long> studentIds) {
		if (!studentIds.isEmpty()) {
			studentProfileRepository.findAllByIdForUpdate(studentIds);
		}
	}

	private Optional<DriveRound> finalRound(Long driveId) {
		Short max = roundRepository.findMaxRoundNo(driveId);
		return max == null ? Optional.empty() : roundRepository.findByDriveIdAndRoundNo(driveId, max);
	}

	private Set<Long> clearedIn(DriveRound round, Set<Long> applicationIds) {
		return resultRepository.findWithRoundByApplicationIdIn(applicationIds).stream()
				.filter(r -> r.getRound().getId().equals(round.getId()))
				.filter(r -> r.getResult().cleared())
				.map(r -> r.getApplication().getId())
				.collect(Collectors.toSet());
	}

}
