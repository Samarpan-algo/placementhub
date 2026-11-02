package com.placementhub.round;

import static com.placementhub.round.RoundFixtures.drive;
import static com.placementhub.round.RoundFixtures.round;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.SQLException;
import java.time.Instant;
import java.util.Optional;

import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import com.placementhub.common.BadRequestException;
import com.placementhub.common.ConflictException;
import com.placementhub.common.ResourceNotFoundException;
import com.placementhub.drive.Drive;
import com.placementhub.drive.DriveRepository;
import com.placementhub.drive.DriveStatus;
import com.placementhub.drive.JobType;

@ExtendWith(MockitoExtension.class)
class RoundServiceTest {

	private static final Long DRIVE_ID = 7L;
	private static final Instant AT = Instant.parse("2026-11-10T04:30:00Z");

	@Mock
	private DriveRepository driveRepository;
	@Mock
	private DriveRoundRepository roundRepository;

	private RoundService service;
	private Drive drive;

	@BeforeEach
	void setUp() {
		service = new RoundService(driveRepository, roundRepository);
		drive = drive(DRIVE_ID, "Nimbus Systems", JobType.FULL_TIME, "12", DriveStatus.CLOSED);
	}

	@Nested
	class Create {

		@Test
		void firstRoundOfADriveIsRound1() {
			when(driveRepository.findById(DRIVE_ID)).thenReturn(Optional.of(drive));
			when(roundRepository.findMaxRoundNo(DRIVE_ID)).thenReturn(null);
			when(roundRepository.saveAndFlush(any(DriveRound.class))).thenAnswer(inv -> {
				DriveRound saved = inv.getArgument(0);
				ReflectionTestUtils.setField(saved, "id", 100L);
				return saved;
			});

			RoundResponse response = service.create(DRIVE_ID, new RoundCreateRequest(1, "  Aptitude ", AT));

			assertThat(response.id()).isEqualTo(100L);
			assertThat(response.roundNo()).isEqualTo(1);
			assertThat(response.name()).isEqualTo("Aptitude");
			assertThat(response.scheduledAt()).isEqualTo(AT);
			assertThat(response.driveId()).isEqualTo(DRIVE_ID);
		}

		@Test
		void nextRoundIsMaxPlusOne() {
			when(driveRepository.findById(DRIVE_ID)).thenReturn(Optional.of(drive));
			when(roundRepository.findMaxRoundNo(DRIVE_ID)).thenReturn((short) 2);
			when(roundRepository.saveAndFlush(any(DriveRound.class))).thenAnswer(inv -> inv.getArgument(0));

			assertThat(service.create(DRIVE_ID, new RoundCreateRequest(3, "HR", null)).roundNo()).isEqualTo(3);
		}

		@Test
		void gapInNumbersIs409WithTheExpectedNumber() {
			when(driveRepository.findById(DRIVE_ID)).thenReturn(Optional.of(drive));
			when(roundRepository.findMaxRoundNo(DRIVE_ID)).thenReturn((short) 1);

			assertThatThrownBy(() -> service.create(DRIVE_ID, new RoundCreateRequest(3, "HR", null)))
					.isInstanceOf(ConflictException.class)
					.hasMessage("Round numbers must have no gaps: the next round number for this drive is 2");
			verify(roundRepository, never()).saveAndFlush(any());
		}

		@Test
		void existingNumberIs409() {
			when(driveRepository.findById(DRIVE_ID)).thenReturn(Optional.of(drive));
			when(roundRepository.findMaxRoundNo(DRIVE_ID)).thenReturn((short) 2);

			assertThatThrownBy(() -> service.create(DRIVE_ID, new RoundCreateRequest(2, "Again", null)))
					.isInstanceOf(ConflictException.class)
					.hasMessageEndingWith("the next round number for this drive is 3");
		}

		@Test
		void concurrentDuplicateCaughtByUniqueConstraintIs409() {
			when(driveRepository.findById(DRIVE_ID)).thenReturn(Optional.of(drive));
			when(roundRepository.findMaxRoundNo(DRIVE_ID)).thenReturn(null);
			when(roundRepository.saveAndFlush(any(DriveRound.class)))
					.thenThrow(constraintViolation(RoundService.UNIQUE_DRIVE_ROUND_NO));

			assertThatThrownBy(() -> service.create(DRIVE_ID, new RoundCreateRequest(1, "Aptitude", null)))
					.isInstanceOf(ConflictException.class)
					.hasMessage("Round 1 already exists for this drive");
		}

		@ParameterizedTest
		@EnumSource(value = DriveStatus.class, names = { "OPEN", "CLOSED" })
		void allowedWhileOpenOrClosed(DriveStatus status) {
			drive.setStatus(status);
			when(driveRepository.findById(DRIVE_ID)).thenReturn(Optional.of(drive));
			when(roundRepository.findMaxRoundNo(DRIVE_ID)).thenReturn(null);
			when(roundRepository.saveAndFlush(any(DriveRound.class))).thenAnswer(inv -> inv.getArgument(0));

			assertThat(service.create(DRIVE_ID, new RoundCreateRequest(1, "Aptitude", null)).roundNo()).isEqualTo(1);
		}

		@ParameterizedTest
		@EnumSource(value = DriveStatus.class, names = { "DRAFT", "COMPLETED" })
		void blockedInOtherDriveStatuses(DriveStatus status) {
			drive.setStatus(status);
			when(driveRepository.findById(DRIVE_ID)).thenReturn(Optional.of(drive));

			assertThatThrownBy(() -> service.create(DRIVE_ID, new RoundCreateRequest(1, "Aptitude", null)))
					.isInstanceOf(ConflictException.class)
					.hasMessage("Rounds can be added or edited only while the drive is OPEN or CLOSED (status: "
							+ status + ")");
		}

		@Test
		void missingDriveIs404() {
			when(driveRepository.findById(DRIVE_ID)).thenReturn(Optional.empty());

			assertThatThrownBy(() -> service.create(DRIVE_ID, new RoundCreateRequest(1, "Aptitude", null)))
					.isInstanceOf(ResourceNotFoundException.class);
		}

	}

	@Nested
	class Update {

		private DriveRound round;

		@BeforeEach
		void setUp() {
			round = round(100L, drive, 2, "Technical");
		}

		@Test
		void changesNameAndScheduledAt() {
			when(driveRepository.findById(DRIVE_ID)).thenReturn(Optional.of(drive));
			when(roundRepository.findByIdAndDriveId(100L, DRIVE_ID)).thenReturn(Optional.of(round));

			RoundResponse response = service.update(DRIVE_ID, 100L, new RoundUpdateRequest(null, "Tech Interview", AT));

			assertThat(response.name()).isEqualTo("Tech Interview");
			assertThat(response.scheduledAt()).isEqualTo(AT);
			assertThat(response.roundNo()).isEqualTo(2);
		}

		@Test
		void sameRoundNoIsAccepted() {
			when(driveRepository.findById(DRIVE_ID)).thenReturn(Optional.of(drive));
			when(roundRepository.findByIdAndDriveId(100L, DRIVE_ID)).thenReturn(Optional.of(round));

			assertThat(service.update(DRIVE_ID, 100L, new RoundUpdateRequest(2, "Technical", null)).roundNo())
					.isEqualTo(2);
		}

		@Test
		void differentRoundNoIs400AndNothingChanges() {
			when(driveRepository.findById(DRIVE_ID)).thenReturn(Optional.of(drive));
			when(roundRepository.findByIdAndDriveId(100L, DRIVE_ID)).thenReturn(Optional.of(round));

			assertThatThrownBy(() -> service.update(DRIVE_ID, 100L, new RoundUpdateRequest(3, "Renamed", null)))
					.isInstanceOf(BadRequestException.class)
					.hasMessage("roundNo cannot be changed (this is Round 2); leave it out or send the same number");
			assertThat(round.getName()).isEqualTo("Technical");
		}

		@Test
		void roundOfAnotherDriveIs404() {
			when(driveRepository.findById(DRIVE_ID)).thenReturn(Optional.of(drive));
			when(roundRepository.findByIdAndDriveId(100L, DRIVE_ID)).thenReturn(Optional.empty());

			assertThatThrownBy(() -> service.update(DRIVE_ID, 100L, new RoundUpdateRequest(null, "X", null)))
					.isInstanceOf(ResourceNotFoundException.class)
					.hasMessage("Round with id 100 not found");
		}

		@Test
		void blockedOnCompletedDrive() {
			drive.setStatus(DriveStatus.COMPLETED);
			when(driveRepository.findById(DRIVE_ID)).thenReturn(Optional.of(drive));
			when(roundRepository.findByIdAndDriveId(100L, DRIVE_ID)).thenReturn(Optional.of(round));

			assertThatThrownBy(() -> service.update(DRIVE_ID, 100L, new RoundUpdateRequest(null, "X", null)))
					.isInstanceOf(ConflictException.class);
			assertThat(round.getName()).isEqualTo("Technical");
		}

	}

	@Test
	void listOfMissingDriveIs404() {
		when(driveRepository.findById(DRIVE_ID)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.list(DRIVE_ID)).isInstanceOf(ResourceNotFoundException.class);
	}

	private static DataIntegrityViolationException constraintViolation(String constraintName) {
		ConstraintViolationException hibernate = new ConstraintViolationException(
				"duplicate key value violates unique constraint \"" + constraintName + "\"",
				new SQLException("23505"), constraintName);
		return new DataIntegrityViolationException("could not execute statement", hibernate);
	}

}
