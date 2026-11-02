package com.placementhub.round;

import java.math.BigDecimal;
import java.time.Instant;

import org.springframework.test.util.ReflectionTestUtils;

import com.placementhub.application.Application;
import com.placementhub.application.ApplicationStatus;
import com.placementhub.common.Branch;
import com.placementhub.company.Company;
import com.placementhub.drive.Drive;
import com.placementhub.drive.DriveStatus;
import com.placementhub.drive.JobType;
import com.placementhub.student.StudentProfile;
import com.placementhub.user.Role;
import com.placementhub.user.User;

/** Test data for rounds, results and selection: entities with ids set, as if loaded from the DB. */
public final class RoundFixtures {

	private RoundFixtures() {
	}

	public static Drive drive(Long id, String companyName, JobType jobType, String ctcLpa, DriveStatus status) {
		Drive drive = new Drive(new Company(companyName, null, null),
				new User("admin@placementhub.dev", "hash", Role.TPO_ADMIN));
		ReflectionTestUtils.setField(drive, "id", id);
		drive.setRoleTitle("Software Engineer");
		drive.setJobType(jobType);
		drive.setCtcLpa(new BigDecimal(ctcLpa));
		drive.setRegistrationDeadline(Instant.parse("2026-10-25T10:00:00Z"));
		drive.setStatus(status);
		return drive;
	}

	public static StudentProfile student(Long id, String name) {
		StudentProfile student = new StudentProfile(new User(name.toLowerCase() + "@student.dev", "hash", Role.STUDENT),
				name, "23CSE" + id, Branch.CSE, (short) 2027, new BigDecimal("8.50"), (short) 0);
		ReflectionTestUtils.setField(student, "id", id);
		return student;
	}

	public static Application application(Long id, Drive drive, StudentProfile student, ApplicationStatus status) {
		Application application = new Application(drive, student);
		ReflectionTestUtils.setField(application, "id", id);
		application.setStatus(status);
		return application;
	}

	public static DriveRound round(Long id, Drive drive, int roundNo, String name) {
		DriveRound round = new DriveRound(drive, (short) roundNo);
		ReflectionTestUtils.setField(round, "id", id);
		round.setName(name);
		return round;
	}

	public static RoundResult result(DriveRound round, Application application, RoundOutcome outcome) {
		RoundResult result = new RoundResult(round, application);
		result.setResult(outcome);
		return result;
	}

}
