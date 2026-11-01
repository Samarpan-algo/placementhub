package com.placementhub.student;

import org.springframework.stereotype.Component;

import com.placementhub.common.ResourceNotFoundException;
import com.placementhub.user.CurrentUserProvider;

/**
 * Finds the profile of the student making the request (user id from the JWT via CurrentUserProvider).
 * Returns an entity, so call it only from inside a service's @Transactional method.
 */
@Component
public class CurrentStudentResolver {

	private final CurrentUserProvider currentUserProvider;
	private final StudentProfileRepository studentProfileRepository;

	public CurrentStudentResolver(CurrentUserProvider currentUserProvider,
			StudentProfileRepository studentProfileRepository) {
		this.currentUserProvider = currentUserProvider;
		this.studentProfileRepository = studentProfileRepository;
	}

	public StudentProfile requireProfile() {
		return studentProfileRepository.findByUserId(currentUserProvider.currentUserId())
				.orElseThrow(() -> new ResourceNotFoundException("Student profile not found"));
	}

}
