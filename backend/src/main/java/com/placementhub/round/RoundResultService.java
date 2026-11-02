package com.placementhub.round;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.placementhub.application.Application;
import com.placementhub.application.ApplicationRepository;
import com.placementhub.application.ApplicationStatus;
import com.placementhub.application.StatusChangeTrigger;
import com.placementhub.application.StatusChanges;
import com.placementhub.audit.AuditAction;
import com.placementhub.audit.AuditEntityType;
import com.placementhub.audit.AuditService;
import com.placementhub.common.BatchValidation;
import com.placementhub.common.ConflictException;
import com.placementhub.common.ResourceNotFoundException;
import com.placementhub.drive.Drive;
import com.placementhub.drive.DriveRepository;
import com.placementhub.drive.DriveStatus;
import com.placementhub.round.RoundStatusCalculator.StatusDecision;
import com.placementhub.student.StudentProfileRepository;

/**
 * Bulk round results (upsert), all or nothing. Rules, all checked for every item before anything is saved:
 * the drive is CLOSED; each application belongs to the drive and appears once; its status still comes
 * from rounds (not SELECTED, WITHDRAWN or INELIGIBLE); round N needs CLEARED in round N-1; and a failing
 * result can't be recorded while the application has a result in a later round.
 * Then each application's status is recalculated from all its results and audited.
 * Every new or changed result is audited too (RESULT_CHANGED), even when the status stays the same.
 */
@Service
public class RoundResultService {

	/** Their status no longer comes from rounds, so a round result can't change it. */
	static final Set<ApplicationStatus> RESULTS_LOCKED = EnumSet.of(ApplicationStatus.SELECTED,
			ApplicationStatus.WITHDRAWN, ApplicationStatus.INELIGIBLE);

	private final DriveRepository driveRepository;
	private final DriveRoundRepository roundRepository;
	private final RoundResultRepository resultRepository;
	private final ApplicationRepository applicationRepository;
	private final StudentProfileRepository studentProfileRepository;
	private final StatusChanges statusChanges;
	private final AuditService auditService;

	public RoundResultService(DriveRepository driveRepository, DriveRoundRepository roundRepository,
			RoundResultRepository resultRepository, ApplicationRepository applicationRepository,
			StudentProfileRepository studentProfileRepository, StatusChanges statusChanges,
			AuditService auditService) {
		this.driveRepository = driveRepository;
		this.roundRepository = roundRepository;
		this.resultRepository = resultRepository;
		this.applicationRepository = applicationRepository;
		this.studentProfileRepository = studentProfileRepository;
		this.statusChanges = statusChanges;
		this.auditService = auditService;
	}

	@Transactional
	public RoundResultsResponse saveResults(Long driveId, Long roundId, RoundResultsRequest request) {
		// 1. Lock order: the drive, then its students, then load the applications fresh
		Drive drive = driveRepository.findByIdForUpdate(driveId)
				.orElseThrow(() -> ResourceNotFoundException.of("Drive", driveId));
		DriveRound round = roundRepository.findByIdAndDriveId(roundId, driveId)
				.orElseThrow(() -> ResourceNotFoundException.of("Round", roundId));
		if (drive.getStatus() != DriveStatus.CLOSED) {
			throw new ConflictException("Round results can be recorded only while the drive is CLOSED (status: "
					+ drive.getStatus() + ")");
		}

		List<Long> ids = request.results().stream().map(RoundResultsRequest.Item::applicationId).distinct().toList();
		List<Long> studentIds = applicationRepository.findStudentIdsByIdInAndDriveId(ids, driveId);
		if (!studentIds.isEmpty()) {
			studentProfileRepository.findAllByIdForUpdate(studentIds);
		}
		Map<Long, Application> applications = applicationRepository.findByIdInAndDriveId(ids, driveId).stream()
				.collect(Collectors.toMap(Application::getId, Function.identity()));
		Map<Long, List<RoundResult>> resultsByApplication = applications.isEmpty()
				? new HashMap<>()
				: resultRepository.findWithRoundByApplicationIdIn(applications.keySet()).stream()
						.collect(Collectors.groupingBy(r -> r.getApplication().getId(), HashMap::new,
								Collectors.toCollection(ArrayList::new)));
		Optional<DriveRound> previousRound = round.getRoundNo() > 1
				? roundRepository.findByDriveIdAndRoundNo(driveId, (short) (round.getRoundNo() - 1))
				: Optional.empty();

		// 2. Validate every item; nothing is saved if any is invalid
		BatchValidation validation = new BatchValidation();
		Set<Long> seen = new HashSet<>();
		for (RoundResultsRequest.Item item : request.results()) {
			check(item, round, previousRound, applications.get(item.applicationId()),
					resultsByApplication.getOrDefault(item.applicationId(), List.of()), seen, validation);
		}
		validation.throwIfAny("Round results were not saved");

		// 3. Upsert, then recalculate each status from all of its results
		List<RoundResultsResponse.Item> saved = new ArrayList<>();
		for (RoundResultsRequest.Item item : request.results()) {
			Application application = applications.get(item.applicationId());
			List<RoundResult> results = resultsByApplication.computeIfAbsent(application.getId(),
					id -> new ArrayList<>());
			RoundResult result = upsert(round, application, results, item);

			StatusDecision decision = RoundStatusCalculator.statusFrom(results);
			statusChanges.change(application, decision.status(), decision.reason(), StatusChangeTrigger.ROUND_RESULT);
			saved.add(new RoundResultsResponse.Item(application.getId(), application.getStudent().getFullName(),
					application.getStudent().getRollNo(), result.getResult(), result.getRemarks(),
					application.getStatus(), application.getStatusReason()));
		}
		resultRepository.flush();
		return new RoundResultsResponse(round.getId(), round.getRoundNo(), round.getName(), saved);
	}

	private static void check(RoundResultsRequest.Item item, DriveRound round, Optional<DriveRound> previousRound,
			Application application, List<RoundResult> results, Set<Long> seen, BatchValidation validation) {
		Long id = item.applicationId();
		if (!seen.add(id)) {
			validation.badRequest("Application " + id + " is listed more than once in this batch");
			return;
		}
		if (application == null) {
			validation.badRequest("Application " + id + " was not found in this drive");
			return;
		}
		if (RESULTS_LOCKED.contains(application.getStatus())) {
			String reason = application.getStatusReason() == null ? "" : " (" + application.getStatusReason() + ")";
			validation.conflict("Application " + id + " is " + application.getStatus() + reason
					+ "; round results can't be recorded for it");
			return;
		}
		// Sequencing: round N only for applications that CLEARED round N-1
		if (previousRound.isPresent()) {
			DriveRound previous = previousRound.get();
			Optional<RoundResult> previousResult = resultIn(results, previous);
			if (previousResult.isEmpty() || !previousResult.get().getResult().cleared()) {
				String current = previousResult.map(r -> r.getResult().name()).orElse("no result");
				validation.conflict("Application " + id + " must have CLEARED " + previous.label() + " before "
						+ round.label() + " (current result: " + current + ")");
				return;
			}
		}
		// A fail here would contradict a later round the application already took part in
		if (!item.result().cleared()) {
			results.stream()
					.filter(r -> r.getRound().getRoundNo() > round.getRoundNo())
					.min(Comparator.comparing(r -> r.getRound().getRoundNo()))
					.ifPresent(later -> validation.conflict("Application " + id + " already has a result in "
							+ later.getRound().label() + ", so it can't be marked " + item.result() + " in "
							+ round.label()));
		}
	}

	/**
	 * Updates this round's existing result (a correction) or creates it, and audits it if anything changed.
	 * Keeps `results` up to date.
	 */
	private RoundResult upsert(DriveRound round, Application application, List<RoundResult> results,
			RoundResultsRequest.Item item) {
		String remarks = item.remarks() == null || item.remarks().isBlank() ? null : item.remarks().trim();
		Optional<RoundResult> existing = resultIn(results, round);
		if (existing.isPresent()) {
			RoundResult result = existing.get();
			if (result.getResult() == item.result() && Objects.equals(result.getRemarks(), remarks)) {
				return result; // the same sheet sent again: nothing changed, no audit row
			}
			Map<String, Object> oldValue = resultValue(application, round, result.getResult(), result.getRemarks());
			// A managed entity: the UPDATE runs at flush, no save() needed
			result.setResult(item.result());
			result.setRemarks(remarks);
			auditService.record(AuditEntityType.ROUND_RESULT, result.getId(), AuditAction.RESULT_CHANGED, oldValue,
					resultValue(application, round, item.result(), remarks));
			return result;
		}
		// Fields first: with IDENTITY ids, save() runs the INSERT immediately (and gives the id for the audit)
		RoundResult created = new RoundResult(round, application);
		created.setResult(item.result());
		created.setRemarks(remarks);
		results.add(created);
		resultRepository.save(created);
		auditService.record(AuditEntityType.ROUND_RESULT, created.getId(), AuditAction.RESULT_CHANGED, null,
				resultValue(application, round, item.result(), remarks));
		return created;
	}

	/** The audit JSON of one result. The application id and round number make the row readable on its own. */
	private static Map<String, Object> resultValue(Application application, DriveRound round, RoundOutcome result,
			String remarks) {
		Map<String, Object> value = new LinkedHashMap<>(); // remarks may be null, so not Map.of
		value.put("applicationId", application.getId());
		value.put("roundNo", round.getRoundNo());
		value.put("result", result.name());
		value.put("remarks", remarks);
		return value;
	}

	private static Optional<RoundResult> resultIn(List<RoundResult> results, DriveRound round) {
		return results.stream().filter(r -> r.getRound().getId().equals(round.getId())).findFirst();
	}

}
