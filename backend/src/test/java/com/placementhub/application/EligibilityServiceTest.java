package com.placementhub.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import com.placementhub.common.Branch;
import com.placementhub.company.Company;
import com.placementhub.drive.Drive;
import com.placementhub.drive.DriveStatus;
import com.placementhub.drive.JobType;
import com.placementhub.student.StudentProfile;
import com.placementhub.user.Role;
import com.placementhub.user.User;

/** Pure unit tests: no Spring, no mocks, no database. */
class EligibilityServiceTest {

	// 2026-11-01 10:00 UTC = 3:30 pm IST
	private static final Instant NOW = Instant.parse("2026-11-01T10:00:00Z");
	private static final Instant DEADLINE = NOW.plus(Duration.ofDays(10)); // 11 Nov 2026, 3:30 pm IST

	private final EligibilityService service = new EligibilityService();

	// ---------------------------------------------------------------------
	// Baseline: a student who meets every rule
	// ---------------------------------------------------------------------

	@Test
	void studentMeetingEveryRuleIsEligibleWithNoReasons() {
		EligibilityResult result = service.evaluate(student(Branch.CSE, "8.00", 0, 2027), openDrive(), NOW);

		assertThat(result.eligible()).isTrue();
		assertThat(result.reasons()).isEmpty();
	}

	// ---------------------------------------------------------------------
	// Rule 1: drive must be OPEN
	// ---------------------------------------------------------------------

	@Nested
	class DriveStatusRule {

		@ParameterizedTest
		@EnumSource(value = DriveStatus.class, names = { "DRAFT", "CLOSED", "COMPLETED" })
		void failsWhenDriveIsNotOpen(DriveStatus status) {
			Drive drive = openDrive();
			drive.setStatus(status);

			EligibilityResult result = service.evaluate(eligibleStudent(), drive, NOW);

			assertThat(result.eligible()).isFalse();
			assertThat(result.reasons())
					.containsExactly("This drive is not open for applications (status: " + status + ")");
		}

	}

	// ---------------------------------------------------------------------
	// Rule 2: now must be strictly before the registration deadline
	// ---------------------------------------------------------------------

	@Nested
	class DeadlineRule {

		@Test
		void passesOneSecondBeforeDeadline() {
			Drive drive = openDrive();
			drive.setRegistrationDeadline(NOW.plusSeconds(1));

			assertThat(service.evaluate(eligibleStudent(), drive, NOW).eligible()).isTrue();
		}

		@Test
		void failsWhenDeadlineIsExactlyNow() {
			Drive drive = openDrive();
			drive.setRegistrationDeadline(NOW);

			EligibilityResult result = service.evaluate(eligibleStudent(), drive, NOW);

			assertThat(result.eligible()).isFalse();
			// NOW is 10:00 UTC = 3:30 pm IST
			assertThat(result.reasons()).containsExactly("Registration closed on 1 Nov 2026, 3:30 PM IST");
		}

		@Test
		void failsWhenDeadlineHasPassed() {
			Drive drive = openDrive();
			drive.setRegistrationDeadline(Instant.parse("2026-10-27T13:00:00Z")); // 6:30 pm IST

			EligibilityResult result = service.evaluate(eligibleStudent(), drive, NOW);

			assertThat(result.reasons()).containsExactly("Registration closed on 27 Oct 2026, 6:30 PM IST");
		}

	}

	// ---------------------------------------------------------------------
	// Rule 3: student's branch must be allowed
	// ---------------------------------------------------------------------

	@Nested
	class BranchRule {

		@Test
		void passesWhenBranchIsListed() {
			Drive drive = openDrive(); // CSE, IT, ECE

			assertThat(service.evaluate(student(Branch.ECE, "8.00", 0, 2027), drive, NOW).eligible()).isTrue();
		}

		@Test
		void failsWhenBranchIsNotListedAndNamesTheAllowedBranches() {
			EligibilityResult result = service.evaluate(student(Branch.ME, "8.00", 0, 2027), openDrive(), NOW);

			assertThat(result.eligible()).isFalse();
			assertThat(result.reasons()).containsExactly("Open only to CSE, IT, ECE; your branch is ME");
		}

	}

	// ---------------------------------------------------------------------
	// Rule 4: CGPA >= minimum
	// ---------------------------------------------------------------------

	@Nested
	class CgpaRule {

		@Test
		void passesWhenCgpaExactlyEqualsMinimum() {
			assertThat(service.evaluate(student(Branch.CSE, "7.50", 0, 2027), openDrive(), NOW).eligible()).isTrue();
		}

		@Test
		void passesWhenCgpaEqualsMinimumWithDifferentScale() {
			// BigDecimal.equals("7.5", "7.50") is false; compareTo treats them as equal
			assertThat(service.evaluate(student(Branch.CSE, "7.5", 0, 2027), openDrive(), NOW).eligible()).isTrue();
		}

		@Test
		void failsWhenCgpaIsJustBelowMinimum() {
			EligibilityResult result = service.evaluate(student(Branch.CSE, "7.49", 0, 2027), openDrive(), NOW);

			assertThat(result.eligible()).isFalse();
			assertThat(result.reasons()).containsExactly("Minimum CGPA is 7.50; yours is 7.49");
		}

		@Test
		void messageAlwaysShowsTwoDecimals() {
			Drive drive = openDrive();
			drive.setMinCgpa(new BigDecimal("8"));

			EligibilityResult result = service.evaluate(student(Branch.CSE, "7.2", 0, 2027), drive, NOW);

			assertThat(result.reasons()).containsExactly("Minimum CGPA is 8.00; yours is 7.20");
		}

	}

	// ---------------------------------------------------------------------
	// Rule 5: active backlogs <= maximum
	// ---------------------------------------------------------------------

	@Nested
	class BacklogRule {

		@Test
		void passesWhenBacklogsExactlyEqualMaximum() {
			Drive drive = openDrive();
			drive.setMaxBacklogs((short) 2);

			assertThat(service.evaluate(student(Branch.CSE, "8.00", 2, 2027), drive, NOW).eligible()).isTrue();
		}

		@Test
		void failsWhenOneOverMaximum() {
			Drive drive = openDrive();
			drive.setMaxBacklogs((short) 1);

			EligibilityResult result = service.evaluate(student(Branch.CSE, "8.00", 2, 2027), drive, NOW);

			assertThat(result.reasons()).containsExactly("At most 1 active backlog allowed; you have 2");
		}

		@Test
		void usesPluralForMaximumAboveOne() {
			Drive drive = openDrive();
			drive.setMaxBacklogs((short) 2);

			EligibilityResult result = service.evaluate(student(Branch.CSE, "8.00", 3, 2027), drive, NOW);

			assertThat(result.reasons()).containsExactly("At most 2 active backlogs allowed; you have 3");
		}

		@Test
		void zeroMaximumHasItsOwnMessage() {
			EligibilityResult result = service.evaluate(student(Branch.CSE, "8.00", 1, 2027), openDrive(), NOW);

			assertThat(result.reasons()).containsExactly("No active backlogs allowed; you have 1");
		}

	}

	// ---------------------------------------------------------------------
	// Rule 6: graduation year must match
	// ---------------------------------------------------------------------

	@Nested
	class GraduationYearRule {

		@Test
		void failsForEarlierBatch() {
			EligibilityResult result = service.evaluate(student(Branch.CSE, "8.00", 0, 2026), openDrive(), NOW);

			assertThat(result.reasons()).containsExactly("Only for the 2027 batch; you graduate in 2026");
		}

		@Test
		void failsForLaterBatch() {
			EligibilityResult result = service.evaluate(student(Branch.CSE, "8.00", 0, 2028), openDrive(), NOW);

			assertThat(result.reasons()).containsExactly("Only for the 2027 batch; you graduate in 2028");
		}

	}

	// ---------------------------------------------------------------------
	// Multiple failures: every reason is reported, in rule order
	// ---------------------------------------------------------------------

	@Nested
	class MultipleFailures {

		@Test
		void karanOnSoftwareEngineerDriveGetsThreeReasons() {
			// Seed data: Karan is ME, CGPA 6.20, 2 backlogs, 2027 batch
			EligibilityResult result = service.evaluate(student(Branch.ME, "6.20", 2, 2027), openDrive(), NOW);

			assertThat(result.eligible()).isFalse();
			assertThat(result.reasons()).containsExactly(
					"Open only to CSE, IT, ECE; your branch is ME",
					"Minimum CGPA is 7.50; yours is 6.20",
					"No active backlogs allowed; you have 2");
		}

		@Test
		void everyRuleFailingGivesSixReasons() {
			Drive drive = openDrive();
			drive.setStatus(DriveStatus.CLOSED);
			drive.setRegistrationDeadline(NOW.minusSeconds(60));

			EligibilityResult result = service.evaluate(student(Branch.ME, "5.00", 3, 2026), drive, NOW);

			assertThat(result.eligible()).isFalse();
			assertThat(result.reasons()).hasSize(6);
			assertThat(result.reasons().get(0)).startsWith("This drive is not open");
			assertThat(result.reasons().get(1)).startsWith("Registration closed on");
			assertThat(result.reasons().get(2)).startsWith("Open only to");
			assertThat(result.reasons().get(3)).startsWith("Minimum CGPA");
			assertThat(result.reasons().get(4)).startsWith("No active backlogs");
			assertThat(result.reasons().get(5)).startsWith("Only for the 2027 batch");
		}

		@Test
		void reasonsListCannotBeModifiedByCallers() {
			EligibilityResult result = service.evaluate(student(Branch.ME, "8.00", 0, 2027), openDrive(), NOW);

			assertThatThrownBy(() -> result.reasons().add("x"))
					.isInstanceOf(UnsupportedOperationException.class);
		}

	}

	// ---------------------------------------------------------------------
	// helpers
	// ---------------------------------------------------------------------

	/** Like the seeded "Software Engineer" drive: OPEN, CSE/IT/ECE, CGPA >= 7.50, 0 backlogs, 2027 batch. */
	private static Drive openDrive() {
		Drive drive = new Drive(new Company("Nimbus Systems", null, null),
				new User("admin@placementhub.dev", "hash", Role.TPO_ADMIN));
		drive.setRoleTitle("Software Engineer");
		drive.setJobType(JobType.FULL_TIME);
		drive.setCtcLpa(new BigDecimal("12.00"));
		drive.setMinCgpa(new BigDecimal("7.50"));
		drive.setMaxBacklogs((short) 0);
		drive.setGraduationYear((short) 2027);
		drive.setRegistrationDeadline(DEADLINE);
		drive.setBranches(Set.of(Branch.CSE, Branch.IT, Branch.ECE));
		drive.setStatus(DriveStatus.OPEN);
		return drive;
	}

	private static StudentProfile eligibleStudent() {
		return student(Branch.CSE, "8.70", 0, 2027);
	}

	private static StudentProfile student(Branch branch, String cgpa, int backlogs, int graduationYear) {
		User user = new User("student@student.placementhub.dev", "hash", Role.STUDENT);
		return new StudentProfile(user, "Test Student", "23CSE999", branch, (short) graduationYear,
				new BigDecimal(cgpa), (short) backlogs);
	}

}
