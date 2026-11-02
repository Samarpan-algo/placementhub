package com.placementhub.round;

import java.net.URI;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import jakarta.validation.Valid;

/** Admin endpoints for a drive's rounds (TPO_ADMIN via /api/drives/** in SecurityConfig). */
@RestController
@RequestMapping("/api/drives/{driveId}/rounds")
public class RoundController {

	private final RoundService roundService;

	public RoundController(RoundService roundService) {
		this.roundService = roundService;
	}

	@PostMapping
	public ResponseEntity<RoundResponse> create(@PathVariable Long driveId,
			@Valid @RequestBody RoundCreateRequest request) {
		RoundResponse created = roundService.create(driveId, request);
		URI location = ServletUriComponentsBuilder.fromCurrentRequest()
				.path("/{id}")
				.buildAndExpand(created.id())
				.toUri();
		return ResponseEntity.created(location).body(created);
	}

	@GetMapping
	public List<RoundResponse> list(@PathVariable Long driveId) {
		return roundService.list(driveId);
	}

	@PutMapping("/{roundId}")
	public RoundResponse update(@PathVariable Long driveId, @PathVariable Long roundId,
			@Valid @RequestBody RoundUpdateRequest request) {
		return roundService.update(driveId, roundId, request);
	}

}
