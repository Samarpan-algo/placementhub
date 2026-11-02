package com.placementhub.audit;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import com.placementhub.common.PageResponse;
import com.placementhub.support.DbFixtures;
import com.placementhub.support.PostgresTest;
import com.placementhub.user.User;
import com.placementhub.user.UserRepository;

import jakarta.persistence.EntityManager;
import tools.jackson.databind.json.JsonMapper;

/** The real audit query on PostgreSQL: null filters, newest first, the date range, JSONB back as JSON. */
@PostgresTest
class AuditQueryServiceDbTest {

	// Far in the future, so these rows are the newest in the dev DB and the ranges contain only them
	private static final Instant T1 = Instant.parse("2099-01-01T10:00:00Z");
	private static final Instant T2 = Instant.parse("2099-01-02T10:00:00Z");
	private static final Instant T3 = Instant.parse("2099-01-03T10:00:00Z");

	@Autowired
	private EntityManager em;
	@Autowired
	private AuditLogRepository auditLogRepository;
	@Autowired
	private UserRepository userRepository;

	private AuditQueryService service;
	private User admin;
	private long driveId;
	private long applicationId;

	@BeforeEach
	void setUp() {
		service = new AuditQueryService(auditLogRepository, userRepository, JsonMapper.builder().build());
		DbFixtures db = new DbFixtures(em);
		admin = db.admin();
		driveId = ThreadLocalRandom.current().nextLong(1_000_000_000L, 2_000_000_000L);
		applicationId = driveId + 1;
		save(AuditEntityType.DRIVE, driveId, AuditAction.CRITERIA_CHANGED, "{\"minCgpa\": 7.50}",
				"{\"minCgpa\": 8.00, \"applicationsMadeIneligible\": 1}", admin.getId(), T1);
		save(AuditEntityType.APPLICATION, applicationId, AuditAction.STATUS_CHANGED,
				"{\"status\": \"APPLIED\", \"reason\": null}",
				"{\"status\": \"INELIGIBLE\", \"reason\": \"Drive criteria changed: \\\"x\\\"\"}", admin.getId(), T2);
		save(AuditEntityType.DRIVE, driveId, AuditAction.CRITERIA_CHANGED, "{}", "{}", null, T3);
		db.flushAndClear();
	}

	private void save(AuditEntityType type, long id, AuditAction action, String oldValue, String newValue,
			Long actorId, Instant at) {
		auditLogRepository.save(new AuditLog(type, id, action, oldValue, newValue, actorId, at));
	}

	@Test
	void noFiltersReturnsEverythingNewestFirst() {
		PageResponse<AuditLogResponse> page = service.search(null, null, null, null, PageRequest.of(0, 3));

		assertThat(page.content()).extracting(AuditLogResponse::createdAt).containsExactly(T3, T2, T1);
	}

	@Test
	void clientSortIsIgnored() {
		PageResponse<AuditLogResponse> page = service.search(null, null, null, null,
				PageRequest.of(0, 3, Sort.by("createdAt").ascending()));

		assertThat(page.content()).extracting(AuditLogResponse::createdAt).containsExactly(T3, T2, T1);
	}

	@Test
	void filtersByEntityTypeAndId() {
		PageResponse<AuditLogResponse> page = service.search(AuditEntityType.DRIVE, driveId, null, null,
				PageRequest.of(0, 20));

		assertThat(page.totalElements()).isEqualTo(2);
		assertThat(page.content()).allSatisfy(row -> assertThat(row.entityId()).isEqualTo(driveId));
	}

	@Test
	void filtersByEntityTypeAlone() {
		PageResponse<AuditLogResponse> page = service.search(AuditEntityType.APPLICATION, null, T1, null,
				PageRequest.of(0, 20));

		assertThat(page.content()).extracting(AuditLogResponse::entityId).containsExactly(applicationId);
	}

	@Test
	void fromIsInclusiveAndToIsExclusive() {
		PageResponse<AuditLogResponse> page = service.search(null, null, T1, T3, PageRequest.of(0, 20));

		assertThat(page.content()).extracting(AuditLogResponse::createdAt).containsExactly(T2, T1);
	}

	@Test
	void valuesComeBackAsJsonAndTheActorEmailIsResolved() {
		List<AuditLogResponse> rows = service.search(AuditEntityType.APPLICATION, applicationId, null, null,
				PageRequest.of(0, 20)).content();

		AuditLogResponse row = rows.get(0);
		assertThat(row.oldValue().get("status").asString()).isEqualTo("APPLIED");
		assertThat(row.oldValue().get("reason").isNull()).isTrue();
		assertThat(row.newValue().get("reason").asString()).isEqualTo("Drive criteria changed: \"x\"");
		assertThat(row.actorEmail()).isEqualTo(admin.getEmail());
	}

	@Test
	void aSystemActionHasNoActorEmail() {
		AuditLogResponse newest = service.search(AuditEntityType.DRIVE, driveId, null, null, PageRequest.of(0, 1))
				.content().get(0);

		assertThat(newest.actorId()).isNull();
		assertThat(newest.actorEmail()).isNull();
	}

}
