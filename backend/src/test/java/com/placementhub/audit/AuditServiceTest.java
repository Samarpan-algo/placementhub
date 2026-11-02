package com.placementhub.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.placementhub.user.CurrentUserProvider;

import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class AuditServiceTest {

	private static final Instant NOW = Instant.parse("2026-11-01T10:00:00Z");

	@Mock
	private AuditLogRepository auditLogRepository;
	@Mock
	private CurrentUserProvider currentUserProvider;

	private AuditService auditService;

	@BeforeEach
	void setUp() {
		auditService = new AuditService(auditLogRepository, currentUserProvider, JsonMapper.builder().build(),
				Clock.fixed(NOW, ZoneOffset.UTC));
		when(currentUserProvider.currentUserId()).thenReturn(1L);
	}

	@Test
	void writesWhoWhatOldNewAndWhen() {
		Map<String, Object> oldValue = new LinkedHashMap<>();
		oldValue.put("status", "APPLIED");
		oldValue.put("reason", null);
		Map<String, Object> newValue = new LinkedHashMap<>();
		newValue.put("status", "REJECTED");
		newValue.put("reason", "Not cleared in Round 1: \"Aptitude\"");

		auditService.record(AuditEntityType.APPLICATION, 50L, AuditAction.STATUS_CHANGED, oldValue, newValue);

		AuditLog saved = captureSaved();
		assertThat(saved.getEntityType()).isEqualTo(AuditEntityType.APPLICATION);
		assertThat(saved.getEntityId()).isEqualTo(50L);
		assertThat(saved.getAction()).isEqualTo(AuditAction.STATUS_CHANGED);
		assertThat(saved.getActorId()).isEqualTo(1L);
		assertThat(saved.getCreatedAt()).isEqualTo(NOW);
		assertThat(saved.getOldValue()).isEqualTo("{\"status\":\"APPLIED\",\"reason\":null}");
		// quotes inside the reason are escaped, so the JSON stays valid
		assertThat(saved.getNewValue())
				.isEqualTo("{\"status\":\"REJECTED\",\"reason\":\"Not cleared in Round 1: \\\"Aptitude\\\"\"}");
	}

	@Test
	void nullOldValueStaysNullForNewEntities() {
		auditService.record(AuditEntityType.APPLICATION, 50L, AuditAction.STATUS_CHANGED, null,
				Map.of("status", "APPLIED"));

		assertThat(captureSaved().getOldValue()).isNull();
	}

	private AuditLog captureSaved() {
		ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
		verify(auditLogRepository).save(captor.capture());
		return captor.getValue();
	}

}
