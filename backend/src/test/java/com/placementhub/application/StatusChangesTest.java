package com.placementhub.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.placementhub.audit.AuditAction;
import com.placementhub.audit.AuditEntityType;
import com.placementhub.audit.AuditService;
import com.placementhub.policy.PolicyFixtures;
import com.placementhub.drive.JobType;

@ExtendWith(MockitoExtension.class)
class StatusChangesTest {

	@Mock
	private AuditService auditService;

	private StatusChanges statusChanges;
	private Application application;

	@BeforeEach
	void setUp() {
		statusChanges = new StatusChanges(auditService);
		application = new Application(PolicyFixtures.drive(JobType.FULL_TIME, "12"), null);
		ReflectionTestUtils.setField(application, "id", 50L);
		application.setStatus(ApplicationStatus.SHORTLISTED);
	}

	@Test
	@SuppressWarnings({ "unchecked", "rawtypes" })
	void changeSetsStatusAndReasonAndAuditsOldToNewWithTrigger() {
		boolean changed = statusChanges.change(application, ApplicationStatus.REJECTED,
				"Not cleared in Round 1: Aptitude", StatusChangeTrigger.ROUND_RESULT);

		assertThat(changed).isTrue();
		assertThat(application.getStatus()).isEqualTo(ApplicationStatus.REJECTED);
		assertThat(application.getStatusReason()).isEqualTo("Not cleared in Round 1: Aptitude");
		ArgumentCaptor<Map<String, Object>> oldValue = (ArgumentCaptor) ArgumentCaptor.forClass(Map.class);
		ArgumentCaptor<Map<String, Object>> newValue = (ArgumentCaptor) ArgumentCaptor.forClass(Map.class);
		verify(auditService).record(eq(AuditEntityType.APPLICATION), eq(50L), eq(AuditAction.STATUS_CHANGED),
				oldValue.capture(), newValue.capture());
		assertThat(oldValue.getValue()).containsEntry("status", "SHORTLISTED").containsEntry("reason", null)
				.doesNotContainKey("trigger");
		assertThat(newValue.getValue()).containsEntry("status", "REJECTED")
				.containsEntry("reason", "Not cleared in Round 1: Aptitude")
				.containsEntry("trigger", "ROUND_RESULT");
	}

	@Test
	void sameStatusAndReasonIsNotAChangeAndWritesNoAudit() {
		boolean changed = statusChanges.change(application, ApplicationStatus.SHORTLISTED, null,
				StatusChangeTrigger.ROUND_RESULT);

		assertThat(changed).isFalse();
		verify(auditService, never()).record(any(), anyLong(), any(), any(), any());
	}

	@Test
	void sameStatusWithANewReasonIsAChange() {
		application.setStatus(ApplicationStatus.REJECTED);
		application.setStatusReason("Not cleared in Round 1: Aptitude");

		boolean changed = statusChanges.change(application, ApplicationStatus.REJECTED,
				"Not cleared in Round 2: Technical", StatusChangeTrigger.ROUND_RESULT);

		assertThat(changed).isTrue();
		verify(auditService).record(any(), anyLong(), any(), any(), any());
	}

}
