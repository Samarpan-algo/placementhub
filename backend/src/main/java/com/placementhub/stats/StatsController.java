package com.placementhub.stats;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Admin: placement statistics (TPO_ADMIN via /api/stats/** in SecurityConfig). */
@RestController
@RequestMapping("/api/stats")
public class StatsController {

	private final StatsService statsService;

	public StatsController(StatsService statsService) {
		this.statsService = statsService;
	}

	@GetMapping
	public StatsResponse stats() {
		return statsService.stats();
	}

}
