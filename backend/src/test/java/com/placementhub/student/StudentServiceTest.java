package com.placementhub.student;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.placementhub.common.Branch;
import com.placementhub.common.ResourceNotFoundException;
import com.placementhub.user.Role;
import com.placementhub.user.User;

@ExtendWith(MockitoExtension.class)
class StudentServiceTest {

	@Mock
	private CurrentStudentResolver currentStudentResolver;
	@Mock
	private StudentProfileRepository studentProfileRepository;

	private StudentService studentService;
	private StudentProfile priya;

	@BeforeEach
	void setUp() {
		studentService = new StudentService(currentStudentResolver, studentProfileRepository,
				new StudentProfileMapper());
		User user = new User("priya.nair@student.placementhub.dev", "hash", Role.STUDENT);
		priya = new StudentProfile(user, "Priya Nair", "23CSE014", Branch.CSE, (short) 2027,
				new BigDecimal("6.80"), (short) 0);
	}

	@Test
	void updateChangesOnlyNameAndPhone() {
		when(currentStudentResolver.requireProfile()).thenReturn(priya);

		StudentProfileResponse response = studentService
				.updateMyProfile(new StudentProfileUpdateRequest("  Priya S. Nair  ", "9876500099"));

		assertThat(response.fullName()).isEqualTo("Priya S. Nair");
		assertThat(response.phone()).isEqualTo("9876500099");
		// academic fields are untouched
		assertThat(response.cgpa()).isEqualByComparingTo("6.80");
		assertThat(response.activeBacklogs()).isZero();
		assertThat(response.branch()).isEqualTo(Branch.CSE);
		assertThat(response.graduationYear()).isEqualTo(2027);
		assertThat(response.rollNo()).isEqualTo("23CSE014");
		verify(studentProfileRepository).flush();
	}

	@Test
	void blankPhoneIsStoredAsNull() {
		when(currentStudentResolver.requireProfile()).thenReturn(priya);

		StudentProfileResponse response = studentService
				.updateMyProfile(new StudentProfileUpdateRequest("Priya Nair", "  "));

		assertThat(response.phone()).isNull();
	}

	@Test
	void userWithoutProfileGets404() {
		when(currentStudentResolver.requireProfile())
				.thenThrow(new ResourceNotFoundException("Student profile not found"));

		assertThatThrownBy(() -> studentService.updateMyProfile(new StudentProfileUpdateRequest("X", null)))
				.isInstanceOf(ResourceNotFoundException.class)
				.hasMessage("Student profile not found");
		verify(studentProfileRepository, never()).flush();
	}

}
