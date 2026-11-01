package com.placementhub.drive;

import java.net.URI;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import com.placementhub.common.PageResponse;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/drives")
public class DriveController {

	private final DriveService driveService;

	public DriveController(DriveService driveService) {
		this.driveService = driveService;
	}

	@PostMapping
	public ResponseEntity<DriveResponse> create(@Valid @RequestBody DriveCreateRequest request) {
		DriveResponse created = driveService.create(request);
		URI location = ServletUriComponentsBuilder.fromCurrentRequest()
				.path("/{id}")
				.buildAndExpand(created.id())
				.toUri();
		return ResponseEntity.created(location).body(created);
	}

	@GetMapping("/{id}")
	public DriveResponse getById(@PathVariable Long id) {
		return driveService.getById(id);
	}

	@GetMapping
	public PageResponse<DriveResponse> list(
			@RequestParam(required = false) DriveStatus status,
			@RequestParam(required = false) Long companyId,
			@PageableDefault(size = 20, sort = "registrationDeadline", direction = Sort.Direction.ASC) Pageable pageable) {
		return driveService.list(status, companyId, pageable);
	}

	@PutMapping("/{id}")
	public DriveResponse update(@PathVariable Long id, @Valid @RequestBody DriveUpdateRequest request) {
		return driveService.update(id, request);
	}

	@PostMapping("/{id}/open")
	public DriveResponse open(@PathVariable Long id) {
		return driveService.open(id);
	}

	@PostMapping("/{id}/close")
	public DriveResponse close(@PathVariable Long id) {
		return driveService.close(id);
	}

	@PostMapping("/{id}/complete")
	public DriveResponse complete(@PathVariable Long id) {
		return driveService.complete(id);
	}

}
