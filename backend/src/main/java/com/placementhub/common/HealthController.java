package com.placementhub.common;

import java.sql.Connection;
import java.sql.SQLException;

import javax.sql.DataSource;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/health")
public class HealthController {

	private static final int DB_CHECK_TIMEOUT_SECONDS = 2;

	private final DataSource dataSource;

	public HealthController(DataSource dataSource) {
		this.dataSource = dataSource;
	}

	@GetMapping
	public ResponseEntity<HealthResponse> health() {
		try (Connection connection = dataSource.getConnection()) {
			if (connection.isValid(DB_CHECK_TIMEOUT_SECONDS)) {
				return ResponseEntity.ok(HealthResponse.up());
			}
		} catch (SQLException e) {
			// DB unreachable or pool timed out: fall through to DOWN
		}
		return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(HealthResponse.down());
	}

}
