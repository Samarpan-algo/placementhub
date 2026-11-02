package com.placementhub.application;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.placementhub.drive.Drive;
import com.placementhub.round.DriveRound;
import com.placementhub.round.RoundResult;

/** Call toResponse inside a transaction: drive and company are LAZY. */
@Component
public class ApplicationMapper {

	public ApplicationResponse toResponse(Application application) {
		Drive drive = application.getDrive();
		return new ApplicationResponse(
				application.getId(),
				drive.getId(),
				drive.getCompany().getName(),
				drive.getRoleTitle(),
				drive.getJobType(),
				drive.getCtcLpa(),
				application.getStatus(),
				application.getStatusReason(),
				application.getAppliedAt(),
				drive.getRegistrationDeadline());
	}

	/**
	 * @param rounds  every round of the application's drive, in round order
	 * @param results the application's results (any order)
	 */
	public ApplicationDetailResponse toDetailResponse(Application application, List<DriveRound> rounds,
			List<RoundResult> results) {
		// getRound().getId() reads the id from the LAZY proxy, without a query
		Map<Long, RoundResult> resultByRoundId = results.stream()
				.collect(Collectors.toMap(r -> r.getRound().getId(), Function.identity()));
		List<ApplicationDetailResponse.Round> tracking = rounds.stream()
				.map(round -> {
					RoundResult result = resultByRoundId.get(round.getId());
					return new ApplicationDetailResponse.Round(round.getRoundNo(), round.getName(),
							round.getScheduledAt(), result == null ? null : result.getResult(),
							result == null ? null : result.getRemarks());
				})
				.toList();
		ApplicationResponse base = toResponse(application);
		return new ApplicationDetailResponse(base.id(), base.driveId(), base.companyName(), base.roleTitle(),
				base.jobType(), base.ctcLpa(), base.status(), base.statusReason(), base.appliedAt(),
				base.registrationDeadline(), tracking);
	}

}
