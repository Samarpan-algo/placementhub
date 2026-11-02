package com.placementhub.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

import com.placementhub.common.BadRequestException;
import com.placementhub.common.ResourceNotFoundException;
import com.placementhub.drive.DriveRepository;
import com.placementhub.round.DriveRoundRepository;
import com.placementhub.round.RoundResultRepository;

/** Input checks. The query, filters and progress are tested against the real DB in ApplicantServiceDbTest. */
@ExtendWith(MockitoExtension.class)
class ApplicantServiceTest {

	@Mock
	private DriveRepository driveRepository;
	@Mock
	private ApplicationRepository applicationRepository;
	@Mock
	private DriveRoundRepository roundRepository;
	@Mock
	private RoundResultRepository resultRepository;

	private ApplicantService service;

	@BeforeEach
	void setUp() {
		service = new ApplicantService(driveRepository, applicationRepository, roundRepository, resultRepository);
	}

	@ParameterizedTest
	@ValueSource(strings = { "-0.01", "10.01", "11" })
	void minCgpaOutside0To10Is400(String minCgpa) {
		ApplicantFilter filter = new ApplicantFilter(null, null, new BigDecimal(minCgpa));

		assertThatThrownBy(() -> service.list(7L, filter, PageRequest.of(0, 20)))
				.isInstanceOf(BadRequestException.class)
				.hasMessage("minCgpa must be between 0 and 10");
		verifyNoInteractions(driveRepository, applicationRepository);
	}

	@Test
	void unknownDriveIs404ForTheListAndTheExport() {
		when(driveRepository.existsById(99L)).thenReturn(false);
		ApplicantFilter filter = new ApplicantFilter(null, null, null);

		assertThatThrownBy(() -> service.list(99L, filter, PageRequest.of(0, 20)))
				.isInstanceOf(ResourceNotFoundException.class);
		assertThatThrownBy(() -> service.listAll(99L, filter))
				.isInstanceOf(ResourceNotFoundException.class);
		verifyNoInteractions(applicationRepository);
	}

}
