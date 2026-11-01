package com.placementhub.student;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The current student's own profile. Students may change only their name and phone. */
@Service
public class StudentService {

	private final CurrentStudentResolver currentStudentResolver;
	private final StudentProfileRepository studentProfileRepository;
	private final StudentProfileMapper studentProfileMapper;

	public StudentService(CurrentStudentResolver currentStudentResolver,
			StudentProfileRepository studentProfileRepository, StudentProfileMapper studentProfileMapper) {
		this.currentStudentResolver = currentStudentResolver;
		this.studentProfileRepository = studentProfileRepository;
		this.studentProfileMapper = studentProfileMapper;
	}

	@Transactional(readOnly = true)
	public StudentProfileResponse getMyProfile() {
		return studentProfileMapper.toResponse(currentStudentResolver.requireProfile());
	}

	@Transactional
	public StudentProfileResponse updateMyProfile(StudentProfileUpdateRequest request) {
		StudentProfile profile = currentStudentResolver.requireProfile();
		studentProfileMapper.applyUpdate(profile, request);
		studentProfileRepository.flush(); // runs @PreUpdate so the response has the new updatedAt
		return studentProfileMapper.toResponse(profile);
	}

}
