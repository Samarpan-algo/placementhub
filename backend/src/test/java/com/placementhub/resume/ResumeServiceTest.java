package com.placementhub.resume;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.mock.web.MockMultipartFile;

import com.placementhub.common.BadRequestException;
import com.placementhub.common.ExternalServiceException;
import com.placementhub.common.ResourceNotFoundException;
import com.placementhub.student.StudentProfileResponse;

class ResumeServiceTest {

	private static final byte[] PDF = "%PDF-1.7".getBytes(StandardCharsets.US_ASCII);
	private static final Duration TTL = Duration.ofMinutes(5);

	private final ResumeStorage storage = mock(ResumeStorage.class);
	private final ResumeRecords records = mock(ResumeRecords.class);
	private final StudentProfileResponse profile = mock(StudentProfileResponse.class);
	private ResumeService service;

	@BeforeEach
	void setUp() {
		service = new ResumeService(storage, records, new ResumeProperties("fake", TTL, null));
		when(records.currentStudentId()).thenReturn(7L);
	}

	private static MockMultipartFile pdf() {
		return new MockMultipartFile("file", "my cv.pdf", "application/pdf", PDF);
	}

	@Test
	void aFirstUploadStoresTheFileUnderTheStudentWithARandomNameAndDeletesNothing() {
		when(records.replacePath(anyString())).thenReturn(new ResumeRecords.Replaced(profile, null));

		assertThat(service.uploadForCurrentStudent(pdf())).isSameAs(profile);

		ArgumentCaptor<String> path = ArgumentCaptor.forClass(String.class);
		verify(storage).upload(path.capture(), eq(PDF));
		assertThat(path.getValue()).matches("students/7/[0-9a-f-]{36}\\.pdf"); // the student's file name isn't used
		verify(records).replacePath(path.getValue());
		verify(storage, never()).delete(anyString());
	}

	@Test
	void aReplacementStoresTheNewFileThenUpdatesTheDbThenDeletesTheOldFile() {
		when(records.replacePath(anyString())).thenReturn(new ResumeRecords.Replaced(profile, "students/7/old.pdf"));

		service.uploadForCurrentStudent(pdf());

		InOrder order = inOrder(storage, records);
		order.verify(storage).upload(anyString(), eq(PDF));
		order.verify(records).replacePath(anyString());
		order.verify(storage).delete("students/7/old.pdf");
	}

	@Test
	void anInvalidFileNeverReachesTheStorageOrTheDb() {
		MockMultipartFile text = new MockMultipartFile("file", "a.txt", "text/plain", PDF);

		assertThatThrownBy(() -> service.uploadForCurrentStudent(text)).isInstanceOf(BadRequestException.class);

		verify(storage, never()).upload(anyString(), any());
		verify(records, never()).replacePath(anyString());
	}

	@Test
	void whenTheUploadFailsTheDbIsNotTouchedSoTheOldResumeStays() {
		doThrow(ExternalServiceException.unavailable("down")).when(storage).upload(anyString(), any());

		assertThatThrownBy(() -> service.uploadForCurrentStudent(pdf())).isInstanceOf(ExternalServiceException.class);

		verify(records, never()).replacePath(anyString());
		verify(storage, never()).delete(anyString());
	}

	@Test
	void whenTheDbUpdateFailsTheNewFileIsRemovedAndTheErrorStillReported() {
		when(records.replacePath(anyString())).thenThrow(new IllegalStateException("db down"));

		assertThatThrownBy(() -> service.uploadForCurrentStudent(pdf())).isInstanceOf(IllegalStateException.class);

		ArgumentCaptor<String> uploaded = ArgumentCaptor.forClass(String.class);
		verify(storage).upload(uploaded.capture(), any());
		verify(storage).delete(uploaded.getValue());
	}

	@Test
	void whenDeletingTheOldFileFailsTheUploadStillSucceeds() {
		when(records.replacePath(anyString())).thenReturn(new ResumeRecords.Replaced(profile, "students/7/old.pdf"));
		doThrow(ExternalServiceException.badGateway("nope")).when(storage).delete("students/7/old.pdf");

		assertThat(service.uploadForCurrentStudent(pdf())).isSameAs(profile);
	}

	@Test
	void aStudentsLinkIsSignedForFiveMinutes() {
		when(records.currentStudentPath()).thenReturn("students/7/a.pdf");
		when(storage.signedUrl("students/7/a.pdf", TTL)).thenReturn("https://signed");

		assertThat(service.linkForCurrentStudent()).isEqualTo(new ResumeLinkResponse("https://signed", 300));
	}

	@Test
	void anAdminGetsALinkToTheRequestedStudentsFile() {
		when(records.pathOf(9L)).thenReturn("students/9/b.pdf");
		when(storage.signedUrl("students/9/b.pdf", TTL)).thenReturn("https://signed-9");

		assertThat(service.linkForStudent(9L)).isEqualTo(new ResumeLinkResponse("https://signed-9", 300));
	}

	@Test
	void noResumeMeansNoCallToTheStorage() {
		when(records.currentStudentPath()).thenThrow(new ResourceNotFoundException("none"));

		assertThatThrownBy(() -> service.linkForCurrentStudent()).isInstanceOf(ResourceNotFoundException.class);

		verify(storage, never()).signedUrl(anyString(), any());
	}

}
