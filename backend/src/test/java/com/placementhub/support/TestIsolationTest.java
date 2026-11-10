package com.placementhub.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.placementhub.resume.FakeResumeStorage;
import com.placementhub.resume.ResumeStorage;

/** The promise behind every API test: never the dev database, never the real bucket. */
@ApiTest
class TestIsolationTest {

	@Autowired
	private DataSource dataSource;
	@Autowired
	private ResumeStorage resumeStorage;

	@Test
	void testsUseTheTestDatabase() throws Exception {
		try (Connection connection = dataSource.getConnection()) {
			assertThat(connection.getCatalog()).isEqualTo("placementhub_test");
		}
	}

	@Test
	void testsUseTheFakeStorage() {
		assertThat(resumeStorage).isInstanceOf(FakeResumeStorage.class);
	}

}
