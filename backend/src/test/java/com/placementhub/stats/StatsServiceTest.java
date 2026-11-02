package com.placementhub.stats;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.placementhub.drive.DriveStatus;

/** The Java side: percentages and filling in zeros. The SQL is tested in StatsRepositoryDbTest. */
@ExtendWith(MockitoExtension.class)
class StatsServiceTest {

	@Mock
	private StatsRepository statsRepository;
	@InjectMocks
	private StatsService service;

	@ParameterizedTest(name = "{0} of {1} = {2}%")
	@CsvSource({ "0, 0, 0.00", "0, 5, 0.00", "1, 3, 33.33", "2, 3, 66.67", "3, 7, 42.86", "5, 5, 100.00" })
	void percentageHasTwoDecimalsHalfUpAndNoDivisionByZero(long part, long whole, String expected) {
		assertThat(StatsService.percentage(part, whole)).isEqualTo(new java.math.BigDecimal(expected));
	}

	@Test
	void anEmptyDatabaseGivesZerosEverywhere() {
		when(statsRepository.studentsByBranch()).thenReturn(List.of());
		when(statsRepository.offersByCompany()).thenReturn(List.of());
		when(statsRepository.ctcOfBestOffers()).thenReturn(new StatsRepository.CtcRow(null, null, null));
		when(statsRepository.drivesByStatus()).thenReturn(List.of());

		StatsResponse stats = service.stats();

		assertThat(stats.totalStudents()).isZero();
		assertThat(stats.placementPercentage()).isEqualByComparingTo("0");
		assertThat(stats.drivesByStatus()).containsOnlyKeys(DriveStatus.values())
				.allSatisfy((status, count) -> assertThat(count).isZero());
	}

}
