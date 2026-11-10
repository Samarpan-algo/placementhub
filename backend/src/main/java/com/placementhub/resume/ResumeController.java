package com.placementhub.resume;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.placementhub.student.StudentProfileResponse;

/**
 * Roles are set in SecurityConfig: /api/students/me/** is STUDENT only, /api/students/{id}/resume is
 * TPO_ADMIN only. A student's id comes from the JWT, so students can reach only their own resume.
 */
@RestController
public class ResumeController {

	private final ResumeService resumeService;

	public ResumeController(ResumeService resumeService) {
		this.resumeService = resumeService;
	}

	/** Uploads a PDF, replacing any previous one. Answers with the updated profile. */
	@PostMapping("/api/students/me/resume")
	public StudentProfileResponse upload(@RequestParam("file") MultipartFile file) {
		return resumeService.uploadForCurrentStudent(file);
	}

	@GetMapping("/api/students/me/resume")
	public ResumeLinkResponse myResume() {
		return resumeService.linkForCurrentStudent();
	}

	@GetMapping("/api/students/{studentId}/resume")
	public ResumeLinkResponse studentResume(@PathVariable Long studentId) {
		return resumeService.linkForStudent(studentId);
	}

}
