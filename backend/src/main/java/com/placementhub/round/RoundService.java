package com.placementhub.round;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.placementhub.common.BadRequestException;
import com.placementhub.common.ConflictException;
import com.placementhub.common.ConstraintViolations;
import com.placementhub.common.ResourceNotFoundException;
import com.placementhub.drive.Drive;
import com.placementhub.drive.DriveRepository;
import com.placementhub.drive.DriveStatus;

/**
 * Admin management of a drive's rounds. Rules:
 * rounds can be added or edited only while the drive is OPEN or CLOSED;
 * round numbers are contiguous (the next one is always max + 1) and never change,
 * so "the previous round" and "the final round" are always unambiguous.
 */
@Service
public class RoundService {

	static final String UNIQUE_DRIVE_ROUND_NO = "uq_drive_rounds_drive_round_no";

	/** OPEN: rounds can be planned during registration. CLOSED: rounds are being run. */
	static final Set<DriveStatus> ROUND_EDITABLE_STATUSES = EnumSet.of(DriveStatus.OPEN, DriveStatus.CLOSED);

	private final DriveRepository driveRepository;
	private final DriveRoundRepository roundRepository;

	public RoundService(DriveRepository driveRepository, DriveRoundRepository roundRepository) {
		this.driveRepository = driveRepository;
		this.roundRepository = roundRepository;
	}

	@Transactional
	public RoundResponse create(Long driveId, RoundCreateRequest request) {
		Drive drive = findDriveOrThrow(driveId);
		checkRoundsEditable(drive);

		Short max = roundRepository.findMaxRoundNo(driveId);
		int next = (max == null) ? 1 : max + 1;
		if (request.roundNo() != next) {
			throw new ConflictException("Round numbers must have no gaps: the next round number for this drive is "
					+ next);
		}

		DriveRound round = new DriveRound(drive, request.roundNo().shortValue());
		round.setName(request.name().trim());
		round.setScheduledAt(request.scheduledAt());
		try {
			// saveAndFlush runs the INSERT now, so a duplicate is caught here and not at commit time
			return RoundResponse.from(roundRepository.saveAndFlush(round));
		} catch (DataIntegrityViolationException e) {
			// Two admins added the same round number at once; the DB UNIQUE constraint stopped the second
			if (ConstraintViolations.isViolationOf(e, UNIQUE_DRIVE_ROUND_NO)) {
				throw new ConflictException("Round " + request.roundNo() + " already exists for this drive");
			}
			throw e;
		}
	}

	@Transactional(readOnly = true)
	public List<RoundResponse> list(Long driveId) {
		findDriveOrThrow(driveId);
		return roundRepository.findByDriveIdOrderByRoundNoAsc(driveId).stream().map(RoundResponse::from).toList();
	}

	@Transactional
	public RoundResponse update(Long driveId, Long roundId, RoundUpdateRequest request) {
		Drive drive = findDriveOrThrow(driveId);
		DriveRound round = roundRepository.findByIdAndDriveId(roundId, driveId)
				.orElseThrow(() -> ResourceNotFoundException.of("Round", roundId));
		checkRoundsEditable(drive);
		if (request.roundNo() != null && request.roundNo() != round.getRoundNo().intValue()) {
			// Renumbering would silently change which results count as "the previous round"
			throw new BadRequestException("roundNo cannot be changed (this is Round " + round.getRoundNo()
					+ "); leave it out or send the same number");
		}
		round.setName(request.name().trim());
		round.setScheduledAt(request.scheduledAt());
		return RoundResponse.from(round);
	}

	private static void checkRoundsEditable(Drive drive) {
		if (!ROUND_EDITABLE_STATUSES.contains(drive.getStatus())) {
			throw new ConflictException("Rounds can be added or edited only while the drive is OPEN or CLOSED (status: "
					+ drive.getStatus() + ")");
		}
	}

	private Drive findDriveOrThrow(Long driveId) {
		return driveRepository.findById(driveId).orElseThrow(() -> ResourceNotFoundException.of("Drive", driveId));
	}

}
