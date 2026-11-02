package com.placementhub.application;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

/** Admin: final selection for a drive (TPO_ADMIN via /api/drives/** in SecurityConfig). */
@RestController
@RequestMapping("/api/drives/{driveId}/selections")
public class SelectionController {

	private final SelectionService selectionService;

	public SelectionController(SelectionService selectionService) {
		this.selectionService = selectionService;
	}

	@PostMapping
	public SelectionResponse select(@PathVariable Long driveId, @Valid @RequestBody SelectionRequest request) {
		return selectionService.select(driveId, request);
	}

}
