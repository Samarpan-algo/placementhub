package com.placementhub.support;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Set;
import java.util.UUID;

import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;

import com.placementhub.application.Application;
import com.placementhub.application.ApplicationStatus;
import com.placementhub.common.Branch;
import com.placementhub.company.Company;
import com.placementhub.drive.Drive;
import com.placementhub.drive.DriveStatus;
import com.placementhub.drive.JobType;
import com.placementhub.round.DriveRound;
import com.placementhub.round.RoundOutcome;
import com.placementhub.round.RoundResult;
import com.placementhub.student.StudentProfile;
import com.placementhub.user.Role;
import com.placementhub.user.User;

import jakarta.persistence.EntityManager;

/**
 * Inserts real rows for {@link PostgresTest}s. Emails, roll numbers and company names get a random
 * suffix, so they never clash with the seed data (the test's rollback removes them anyway).
 */
public class DbFixtures {

	private final EntityManager em;
	private final String run = UUID.randomUUID().toString().substring(0, 8);
	private int counter;

	public DbFixtures(EntityManager em) {
		this.em = em;
	}

	public User admin() {
		return persist(new User("admin-" + run + "-" + (++counter) + "@test.dev", "hash", Role.TPO_ADMIN));
	}

	public StudentProfile student(String name, Branch branch, String cgpa) {
		int n = ++counter;
		User user = persist(new User("s" + n + "-" + run + "@test.dev", "hash", Role.STUDENT));
		return persist(new StudentProfile(user, name, "T" + run + n, branch, (short) 2027, new BigDecimal(cgpa),
				(short) 0));
	}

	public Company company(String name) {
		return persist(new Company(name + " " + run, null, null));
	}

	public Drive drive(Company company, User admin, JobType jobType, String ctcLpa, DriveStatus status) {
		Drive drive = new Drive(company, admin);
		drive.setRoleTitle("Engineer");
		drive.setJobType(jobType);
		drive.setCtcLpa(new BigDecimal(ctcLpa));
		drive.setMinCgpa(new BigDecimal("6.00"));
		drive.setMaxBacklogs((short) 0);
		drive.setGraduationYear((short) 2027);
		drive.setRegistrationDeadline(Instant.now().plus(7, ChronoUnit.DAYS));
		drive.setBranches(Set.of(Branch.CSE, Branch.IT));
		drive.setStatus(status);
		return persist(drive);
	}

	public Application application(Drive drive, StudentProfile student, ApplicationStatus status) {
		Application application = new Application(drive, student);
		application.setStatus(status);
		return persist(application);
	}

	public DriveRound round(Drive drive, int roundNo, String name) {
		DriveRound round = new DriveRound(drive, (short) roundNo);
		round.setName(name);
		return persist(round);
	}

	public RoundResult result(DriveRound round, Application application, RoundOutcome outcome) {
		RoundResult result = new RoundResult(round, application);
		result.setResult(outcome);
		return persist(result);
	}

	/** Writes everything and empties the persistence context, so the next reads really hit the DB. */
	public void flushAndClear() {
		em.flush();
		em.clear();
	}

	public Statistics statistics() {
		return em.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
	}

	private <T> T persist(T entity) {
		em.persist(entity);
		return entity;
	}

}
