package com.placementhub.application;

import java.net.URI;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import com.placementhub.common.PageResponse;

import jakarta.validation.Valid;

/** The current student's applications. Identity comes from CurrentUserProvider. */
@RestController
@RequestMapping("/api/students/me/applications")
public class ApplicationController {

	private final ApplicationService applicationService;

	public ApplicationController(ApplicationService applicationService) {
		this.applicationService = applicationService;
	}

	@PostMapping
	public ResponseEntity<ApplicationResponse> apply(@Valid @RequestBody ApplyRequest request) {
		ApplicationResponse created = applicationService.apply(request);
		URI location = ServletUriComponentsBuilder.fromCurrentRequest()
				.path("/{id}")
				.buildAndExpand(created.id())
				.toUri();
		return ResponseEntity.created(location).body(created);
	}

	@GetMapping
	public PageResponse<ApplicationResponse> listMine(
			@PageableDefault(size = 20, sort = "appliedAt", direction = Sort.Direction.DESC) Pageable pageable) {
		return applicationService.listMine(pageable);
	}

	@GetMapping("/{id}")
	public ApplicationDetailResponse getMine(@PathVariable Long id) {
		return applicationService.getMine(id);
	}

	@PostMapping("/{id}/withdraw")
	public ApplicationResponse withdraw(@PathVariable Long id) {
		return applicationService.withdraw(id);
	}

}
