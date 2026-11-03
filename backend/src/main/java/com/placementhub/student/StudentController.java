package com.placementhub.student;

import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.placementhub.common.PageResponse;

import jakarta.validation.Valid;

/** Endpoints for the current student ("me"). Identity comes from CurrentUserProvider. */
@RestController
@RequestMapping("/api/students/me")
public class StudentController {

	private final StudentService studentService;
	private final StudentDriveService studentDriveService;

	public StudentController(StudentService studentService, StudentDriveService studentDriveService) {
		this.studentService = studentService;
		this.studentDriveService = studentDriveService;
	}

	@GetMapping
	public StudentProfileResponse getMyProfile() {
		return studentService.getMyProfile();
	}

	@PutMapping
	public StudentProfileResponse updateMyProfile(@Valid @RequestBody StudentProfileUpdateRequest request) {
		return studentService.updateMyProfile(request);
	}

	/** OPEN drives, soonest deadline first. Only page and size are supported (no sort). */
	@GetMapping("/drives")
	public PageResponse<StudentDriveResponse> browseDrives(
			@RequestParam(defaultValue = "false") boolean eligibleOnly,
			@PageableDefault(size = 20) Pageable pageable) {
		return studentDriveService.browse(eligibleOnly, pageable);
	}

	/** One drive with the same fields as a browse item: OPEN drives, or drives the student applied to (else 404). */
	@GetMapping("/drives/{id}")
	public StudentDriveResponse getDrive(@PathVariable Long id) {
		return studentDriveService.getOne(id);
	}

}
