package com.placementhub.audit;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

import com.placementhub.common.BadRequestException;
import com.placementhub.user.UserRepository;

import tools.jackson.databind.json.JsonMapper;

/** Input checks. The query itself is tested against the real DB in AuditQueryServiceDbTest. */
@ExtendWith(MockitoExtension.class)
class AuditQueryServiceTest {

	private static final Instant T1 = Instant.parse("2026-11-01T00:00:00Z");
	private static final Instant T2 = Instant.parse("2026-11-02T00:00:00Z");

	@Mock
	private AuditLogRepository auditLogRepository;
	@Mock
	private UserRepository userRepository;

	private AuditQueryService service;

	@BeforeEach
	void setUp() {
		service = new AuditQueryService(auditLogRepository, userRepository, JsonMapper.builder().build());
	}

	@Test
	void entityIdWithoutEntityTypeIs400() {
		assertThatThrownBy(() -> service.search(null, 5L, null, null, PageRequest.of(0, 20)))
				.isInstanceOf(BadRequestException.class)
				.hasMessage("entityId needs entityType: ids are only unique within one entity type");
		verifyNoInteractions(auditLogRepository);
	}

	@Test
	void fromAfterToIs400() {
		assertThatThrownBy(() -> service.search(null, null, T2, T1, PageRequest.of(0, 20)))
				.isInstanceOf(BadRequestException.class)
				.hasMessage("from must be before to");
	}

	@Test
	void fromEqualToToIs400BecauseTheRangeIsEmpty() {
		assertThatThrownBy(() -> service.search(null, null, T1, T1, PageRequest.of(0, 20)))
				.isInstanceOf(BadRequestException.class);
		verifyNoInteractions(auditLogRepository);
	}

}
