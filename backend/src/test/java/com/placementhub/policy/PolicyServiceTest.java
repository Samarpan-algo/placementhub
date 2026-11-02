package com.placementhub.policy;

import static com.placementhub.policy.PolicyFixtures.dreamOffer;
import static com.placementhub.policy.PolicyFixtures.maxOffers;
import static com.placementhub.policy.PolicyFixtures.oneOffer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.placementhub.audit.AuditAction;
import com.placementhub.audit.AuditEntityType;
import com.placementhub.audit.AuditService;

import com.placementhub.common.BadRequestException;
import com.placementhub.common.ConflictException;
import com.placementhub.common.ResourceNotFoundException;
import com.placementhub.user.CurrentUserProvider;
import com.placementhub.user.Role;
import com.placementhub.user.User;
import com.placementhub.user.UserRepository;

@ExtendWith(MockitoExtension.class)
class PolicyServiceTest {

	private static final Instant NOW = Instant.parse("2026-11-02T10:00:00Z");
	private static final Long ADMIN_ID = 1L;

	@Mock
	private PlacementPolicyRepository policyRepository;
	@Mock
	private UserRepository userRepository;
	@Mock
	private CurrentUserProvider currentUserProvider;
	@Mock
	private AuditService auditService;

	private PolicyService service;
	private final User admin = new User("admin@placementhub.dev", "hash", Role.TPO_ADMIN);

	// the seed state: DREAM_OFFER active at 10, the others off
	private PlacementPolicy one;
	private PlacementPolicy dream;
	private PlacementPolicy max;

	@BeforeEach
	void setUp() {
		service = new PolicyService(policyRepository, userRepository, currentUserProvider, new PolicyMapper(),
				auditService, Clock.fixed(NOW, ZoneOffset.UTC));
		one = oneOffer(false);
		dream = dreamOffer(true, "10.00");
		max = maxOffers(false, 2);
	}

	/** Stubs for an update that gets past input validation. */
	private void lockedRowsAndCurrentAdmin() {
		when(policyRepository.findAllForUpdate()).thenReturn(List.of(one, dream, max));
		when(currentUserProvider.currentUserId()).thenReturn(ADMIN_ID);
		when(userRepository.getReferenceById(ADMIN_ID)).thenReturn(admin);
	}

	@Test
	void listReturnsEveryPolicyInIdOrder() {
		when(policyRepository.findAllByOrderByIdAsc()).thenReturn(List.of(one, dream, max));

		List<PolicyResponse> list = service.list();

		assertThat(list).extracting(PolicyResponse::type)
				.containsExactly(PolicyType.ONE_OFFER, PolicyType.DREAM_OFFER, PolicyType.MAX_OFFERS);
		assertThat(list.get(1).ctcThresholdLpa()).isEqualByComparingTo("10.00");
		assertThat(list.get(2).maxOffers()).isEqualTo(2);
	}

	// =====================================================================
	// successful updates
	// =====================================================================

	@Nested
	class Updating {

		@Test
		void dreamThresholdIsChangedAndWhoAndWhenAreRecorded() {
			lockedRowsAndCurrentAdmin();

			PolicyResponse response = service.update(PolicyType.DREAM_OFFER,
					new PolicyUpdateRequest(true, new BigDecimal("12"), null));

			assertThat(response.active()).isTrue();
			assertThat(response.ctcThresholdLpa()).isEqualTo(new BigDecimal("12.00")); // padded to 2 decimals
			assertThat(response.updatedAt()).isEqualTo(NOW);
			assertThat(dream.getUpdatedBy()).isSameAs(admin);
			assertThat(dream.getUpdatedAt()).isEqualTo(NOW);
		}

		@Test
		void updatesUseTheRowLockNotAPlainRead() {
			lockedRowsAndCurrentAdmin();

			service.update(PolicyType.MAX_OFFERS, new PolicyUpdateRequest(true, null, 3));

			verify(policyRepository).findAllForUpdate();
			verify(policyRepository, never()).findAll();
			assertThat(max.isActive()).isTrue();
			assertThat(max.getMaxOffers()).isEqualTo((short) 3);
		}

		@Test
		void maxOffersCanBeActiveTogetherWithDreamOffer() {
			lockedRowsAndCurrentAdmin();

			assertThat(service.update(PolicyType.MAX_OFFERS, new PolicyUpdateRequest(true, null, 2)).active())
					.isTrue();
		}

		@Test
		void oneOfferCanBeActivatedOnceDreamOfferIsOff() {
			dream.setActive(false);
			lockedRowsAndCurrentAdmin();

			assertThat(service.update(PolicyType.ONE_OFFER, new PolicyUpdateRequest(true, null, null)).active())
					.isTrue();
			assertThat(one.isActive()).isTrue();
		}

		@Test
		void deactivatingIsAlwaysAllowed() {
			lockedRowsAndCurrentAdmin();

			PolicyResponse response = service.update(PolicyType.DREAM_OFFER,
					new PolicyUpdateRequest(false, new BigDecimal("10.00"), null));

			assertThat(response.active()).isFalse();
			assertThat(dream.isActive()).isFalse();
		}

		@Test
		void missingPolicyRowIs404() {
			when(policyRepository.findAllForUpdate()).thenReturn(List.of(dream, max));

			assertThatThrownBy(() -> service.update(PolicyType.ONE_OFFER, new PolicyUpdateRequest(false, null, null)))
					.isInstanceOf(ResourceNotFoundException.class)
					.hasMessage("Policy ONE_OFFER not found");
		}

	}

	// =====================================================================
	// ONE_OFFER and DREAM_OFFER are mutually exclusive
	// =====================================================================

	@Nested
	class MutualExclusion {

		@Test
		void activatingOneOfferWhileDreamOfferIsActiveIs409() {
			when(policyRepository.findAllForUpdate()).thenReturn(List.of(one, dream, max));

			assertThatThrownBy(() -> service.update(PolicyType.ONE_OFFER, new PolicyUpdateRequest(true, null, null)))
					.isInstanceOf(ConflictException.class)
					.hasMessage("Cannot activate ONE_OFFER while DREAM_OFFER is active: the two policies contradict "
							+ "each other. Deactivate DREAM_OFFER first");
			assertThat(one.isActive()).isFalse();
		}

		@Test
		void activatingDreamOfferWhileOneOfferIsActiveIs409() {
			one.setActive(true);
			dream.setActive(false);
			when(policyRepository.findAllForUpdate()).thenReturn(List.of(one, dream, max));

			assertThatThrownBy(() -> service.update(PolicyType.DREAM_OFFER,
					new PolicyUpdateRequest(true, new BigDecimal("10.00"), null)))
					.isInstanceOf(ConflictException.class)
					.hasMessage("Cannot activate DREAM_OFFER while ONE_OFFER is active: the two policies contradict "
							+ "each other. Deactivate ONE_OFFER first");
			assertThat(dream.isActive()).isFalse();
		}

		@Test
		void keepingOneOfferInactiveWhileDreamOfferIsActiveIsFine() {
			lockedRowsAndCurrentAdmin();

			assertThat(service.update(PolicyType.ONE_OFFER, new PolicyUpdateRequest(false, null, null)).active())
					.isFalse();
		}

	}

	// =====================================================================
	// audit (POLICY_CHANGED)
	// =====================================================================

	@Nested
	class Audit {

		@BeforeEach
		void ids() {
			ReflectionTestUtils.setField(dream, "id", 2L);
			ReflectionTestUtils.setField(max, "id", 3L);
		}

		@Test
		@SuppressWarnings({ "unchecked", "rawtypes" })
		void aChangeIsAuditedWithOldAndNewSettings() {
			lockedRowsAndCurrentAdmin();

			service.update(PolicyType.DREAM_OFFER, new PolicyUpdateRequest(true, new BigDecimal("12"), null));

			ArgumentCaptor<Map<String, Object>> oldValue = (ArgumentCaptor) ArgumentCaptor.forClass(Map.class);
			ArgumentCaptor<Map<String, Object>> newValue = (ArgumentCaptor) ArgumentCaptor.forClass(Map.class);
			verify(auditService).record(eq(AuditEntityType.POLICY), eq(2L), eq(AuditAction.POLICY_CHANGED),
					oldValue.capture(), newValue.capture());
			assertThat(oldValue.getValue()).containsEntry("type", "DREAM_OFFER").containsEntry("active", true)
					.containsEntry("ctcThresholdLpa", new BigDecimal("10.00")).containsEntry("maxOffers", null);
			assertThat(newValue.getValue()).containsEntry("ctcThresholdLpa", new BigDecimal("12.00"));
		}

		@Test
		void activatingIsAudited() {
			lockedRowsAndCurrentAdmin();

			service.update(PolicyType.MAX_OFFERS, new PolicyUpdateRequest(true, null, 2));

			verify(auditService).record(eq(AuditEntityType.POLICY), eq(3L), eq(AuditAction.POLICY_CHANGED),
					any(), any());
		}

		@Test
		void aPutThatChangesNothingWritesNoAuditRow() {
			lockedRowsAndCurrentAdmin();

			// "10" and the stored "10.00" are the same threshold
			service.update(PolicyType.DREAM_OFFER, new PolicyUpdateRequest(true, new BigDecimal("10"), null));

			verifyNoInteractions(auditService);
			assertThat(dream.getUpdatedAt()).isEqualTo(NOW); // who saved last is still recorded
		}

		@Test
		void aRejectedActivationWritesNoAuditRow() {
			when(policyRepository.findAllForUpdate()).thenReturn(List.of(one, dream, max));

			assertThatThrownBy(() -> service.update(PolicyType.ONE_OFFER, new PolicyUpdateRequest(true, null, null)))
					.isInstanceOf(ConflictException.class);
			verifyNoInteractions(auditService);
		}

	}

	// =====================================================================
	// per-type settings (400): nothing is read or written
	// =====================================================================

	@Nested
	class Settings {

		@Test
		void dreamOfferWithoutThresholdIs400() {
			assertBadRequest(PolicyType.DREAM_OFFER, new PolicyUpdateRequest(true, null, null),
					"DREAM_OFFER requires ctcThresholdLpa (greater than 0)");
		}

		@Test
		void dreamOfferWithMaxOffersIs400() {
			assertBadRequest(PolicyType.DREAM_OFFER, new PolicyUpdateRequest(true, new BigDecimal("10"), 2),
					"maxOffers does not apply to DREAM_OFFER; leave it out");
		}

		@Test
		void maxOffersWithoutLimitIs400() {
			assertBadRequest(PolicyType.MAX_OFFERS, new PolicyUpdateRequest(true, null, null),
					"MAX_OFFERS requires maxOffers (1 or more)");
		}

		@Test
		void maxOffersWithThresholdIs400() {
			assertBadRequest(PolicyType.MAX_OFFERS, new PolicyUpdateRequest(true, new BigDecimal("10"), 2),
					"ctcThresholdLpa does not apply to MAX_OFFERS; leave it out");
		}

		@Test
		void oneOfferWithThresholdIs400() {
			assertBadRequest(PolicyType.ONE_OFFER, new PolicyUpdateRequest(false, new BigDecimal("10"), null),
					"ctcThresholdLpa does not apply to ONE_OFFER; leave it out");
		}

		@Test
		void oneOfferWithMaxOffersIs400() {
			assertBadRequest(PolicyType.ONE_OFFER, new PolicyUpdateRequest(false, null, 1),
					"maxOffers does not apply to ONE_OFFER; leave it out");
		}

		private void assertBadRequest(PolicyType type, PolicyUpdateRequest request, String message) {
			assertThatThrownBy(() -> service.update(type, request))
					.isInstanceOf(BadRequestException.class)
					.hasMessage(message);
			verifyNoInteractions(policyRepository, userRepository, auditService);
		}

	}

}
