package com.placementhub.resume;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.placementhub.common.ResourceNotFoundException;
import com.placementhub.student.CurrentStudentResolver;
import com.placementhub.student.StudentProfile;
import com.placementhub.student.StudentProfileMapper;
import com.placementhub.student.StudentProfileRepository;
import com.placementhub.student.StudentProfileResponse;

/**
 * The database side of resumes, in short transactions of its own. A separate bean from ResumeService, so
 * the network calls to the storage run outside any transaction and never hold a DB connection or row lock.
 */
@Component
class ResumeRecords {

	/** @param oldPath the file that was replaced; null if this is the student's first resume */
	record Replaced(StudentProfileResponse profile, String oldPath) {
	}

	private final CurrentStudentResolver currentStudentResolver;
	private final StudentProfileRepository studentProfileRepository;
	private final StudentProfileMapper studentProfileMapper;

	ResumeRecords(CurrentStudentResolver currentStudentResolver, StudentProfileRepository studentProfileRepository,
			StudentProfileMapper studentProfileMapper) {
		this.currentStudentResolver = currentStudentResolver;
		this.studentProfileRepository = studentProfileRepository;
		this.studentProfileMapper = studentProfileMapper;
	}

	@Transactional(readOnly = true)
	public Long currentStudentId() {
		return currentStudentResolver.requireProfile().getId();
	}

	/**
	 * Points the student at the new file. The row lock makes two uploads at once take turns: the second
	 * sees the first one's file as "old" and removes it, so no file is left behind.
	 */
	@Transactional
	public Replaced replacePath(String newPath) {
		StudentProfile profile = currentStudentResolver.requireProfileForUpdate();
		String oldPath = profile.getResumePath();
		profile.setResumePath(newPath);
		studentProfileRepository.flush(); // runs @PreUpdate so the response has the new updatedAt
		return new Replaced(studentProfileMapper.toResponse(profile), oldPath);
	}

	@Transactional(readOnly = true)
	public String currentStudentPath() {
		return requirePath(currentStudentResolver.requireProfile().getResumePath(),
				"You have not uploaded a resume yet");
	}

	@Transactional(readOnly = true)
	public String pathOf(Long studentId) {
		StudentProfile profile = studentProfileRepository.findById(studentId)
				.orElseThrow(() -> ResourceNotFoundException.of("Student", studentId));
		return requirePath(profile.getResumePath(), "This student has not uploaded a resume");
	}

	private static String requirePath(String path, String messageIfMissing) {
		if (path == null) {
			throw new ResourceNotFoundException(messageIfMissing);
		}
		return path;
	}

}
