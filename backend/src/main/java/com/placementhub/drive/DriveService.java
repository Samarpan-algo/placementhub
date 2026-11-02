package com.placementhub.drive;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.placementhub.application.SelectionService;
import com.placementhub.common.ConflictException;
import com.placementhub.common.PageResponse;
import com.placementhub.common.ResourceNotFoundException;
import com.placementhub.company.Company;
import com.placementhub.company.CompanyRepository;
import com.placementhub.user.CurrentUserProvider;
import com.placementhub.user.User;
import com.placementhub.user.UserRepository;

/**
 * Business rules for drives. All status rules live here:
 * ALLOWED_TRANSITIONS, EDITABLE_STATUSES, and checkCanOpen().
 * Completing a drive also rejects every application still pending (SelectionService.rejectPending).
 */
@Service
public class DriveService {

	/** The only legal status changes: each status has at most one next status. */
	static final Map<DriveStatus, DriveStatus> ALLOWED_TRANSITIONS = Map.of(
			DriveStatus.DRAFT, DriveStatus.OPEN,
			DriveStatus.OPEN, DriveStatus.CLOSED,
			DriveStatus.CLOSED, DriveStatus.COMPLETED);

	/** Drives can be edited only before registration closes. */
	static final Set<DriveStatus> EDITABLE_STATUSES = EnumSet.of(DriveStatus.DRAFT, DriveStatus.OPEN);

	private final DriveRepository driveRepository;
	private final CompanyRepository companyRepository;
	private final UserRepository userRepository;
	private final CurrentUserProvider currentUserProvider;
	private final DriveMapper driveMapper;
	private final SelectionService selectionService;
	private final Clock clock;

	public DriveService(DriveRepository driveRepository, CompanyRepository companyRepository,
			UserRepository userRepository, CurrentUserProvider currentUserProvider, DriveMapper driveMapper,
			SelectionService selectionService, Clock clock) {
		this.driveRepository = driveRepository;
		this.companyRepository = companyRepository;
		this.userRepository = userRepository;
		this.currentUserProvider = currentUserProvider;
		this.driveMapper = driveMapper;
		this.selectionService = selectionService;
		this.clock = clock;
	}

	// ---------- CRUD ----------

	@Transactional
	public DriveResponse create(DriveCreateRequest request) {
		Company company = companyRepository.findById(request.companyId())
				.orElseThrow(() -> ResourceNotFoundException.of("Company", request.companyId()));
		// getReferenceById gives a proxy for the FK without an extra SELECT
		User creator = userRepository.getReferenceById(currentUserProvider.currentUserId());

		Drive drive = new Drive(company, creator); // status defaults to DRAFT
		driveMapper.applyFields(drive, request);
		checkDates(drive);
		return driveMapper.toResponse(driveRepository.save(drive));
	}

	@Transactional(readOnly = true)
	public DriveResponse getById(Long id) {
		return driveMapper.toResponse(findOrThrow(id));
	}

	@Transactional(readOnly = true)
	public PageResponse<DriveResponse> list(DriveStatus status, Long companyId, Pageable pageable) {
		return PageResponse.from(driveRepository.search(status, companyId, pageable).map(driveMapper::toResponse));
	}

	@Transactional
	public DriveResponse update(Long id, DriveUpdateRequest request) {
		Drive drive = findOrThrow(id);
		if (!EDITABLE_STATUSES.contains(drive.getStatus())) {
			throw new ConflictException("Cannot edit a " + drive.getStatus()
					+ " drive. Only DRAFT or OPEN drives can be edited");
		}
		driveMapper.applyFields(drive, request);
		checkDates(drive);
		if (drive.getStatus() == DriveStatus.OPEN) {
			checkCanOpen(drive, "Cannot save OPEN drive"); // an OPEN drive must stay valid after edits
		}
		// TODO Milestone 7: re-evaluate applications and write an audit log when criteria change
		driveRepository.flush(); // runs @PreUpdate so the response has the new updatedAt
		return driveMapper.toResponse(drive);
	}

	// ---------- status transitions ----------

	@Transactional
	public DriveResponse open(Long id) {
		return changeStatus(id, DriveStatus.OPEN);
	}

	@Transactional
	public DriveResponse close(Long id) {
		return changeStatus(id, DriveStatus.CLOSED);
	}

	@Transactional
	public DriveResponse complete(Long id) {
		return changeStatus(id, DriveStatus.COMPLETED);
	}

	private DriveResponse changeStatus(Long id, DriveStatus target) {
		// Locks the drive row: e.g. no round results or selection can run while the drive is being completed
		Drive drive = driveRepository.findByIdForUpdate(id).orElseThrow(() -> ResourceNotFoundException.of("Drive", id));
		DriveStatus current = drive.getStatus();
		DriveStatus allowedNext = ALLOWED_TRANSITIONS.get(current);
		if (allowedNext != target) {
			String allowed = (allowedNext == null) ? "none, " + current + " is final" : current + " → " + allowedNext;
			throw new ConflictException(
					"Cannot change drive status from " + current + " to " + target + ". Allowed: " + allowed);
		}
		if (target == DriveStatus.OPEN) {
			checkCanOpen(drive, "Cannot open drive");
		}
		drive.setStatus(target);
		if (target == DriveStatus.COMPLETED) {
			selectionService.rejectPending(drive);
		}
		driveRepository.flush();
		return driveMapper.toResponse(drive);
	}

	// ---------- rules ----------

	/** Rules an OPEN drive must satisfy, checked when opening and when editing an OPEN drive. */
	private void checkCanOpen(Drive drive, String context) {
		if (!drive.getRegistrationDeadline().isAfter(Instant.now(clock))) {
			throw new ConflictException(context + ": registration deadline must be in the future");
		}
		if (drive.getBranches().isEmpty()) {
			throw new ConflictException(context + ": select at least one eligible branch");
		}
	}

	private static void checkDates(Drive drive) {
		if (drive.getDriveDate() != null
				&& drive.getDriveDate().isBefore(drive.getRegistrationDeadline().atZone(ZoneOffset.UTC).toLocalDate())) {
			throw new ConflictException("Drive date cannot be before the registration deadline");
		}
	}

	private Drive findOrThrow(Long id) {
		return driveRepository.findById(id).orElseThrow(() -> ResourceNotFoundException.of("Drive", id));
	}

}
