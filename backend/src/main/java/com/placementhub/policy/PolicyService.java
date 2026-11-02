package com.placementhub.policy;

import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.placementhub.audit.AuditAction;
import com.placementhub.audit.AuditEntityType;
import com.placementhub.audit.AuditService;
import com.placementhub.common.BadRequestException;
import com.placementhub.common.ConflictException;
import com.placementhub.common.ResourceNotFoundException;
import com.placementhub.user.CurrentUserProvider;
import com.placementhub.user.UserRepository;

/**
 * Admin configuration of placement policies. Rules:
 * each type has its own required settings (checkSettings), and ONE_OFFER and DREAM_OFFER
 * are never active together (CONFLICTING), checked under a row lock so two admins can't race.
 * Every real change is audited (POLICY_CHANGED, old -> new settings); a PUT that changes nothing is not.
 */
@Service
public class PolicyService {

	/** Policies that contradict each other: ONE_OFFER forbids the upgrade DREAM_OFFER allows. */
	static final Map<PolicyType, PolicyType> CONFLICTING = Map.of(
			PolicyType.ONE_OFFER, PolicyType.DREAM_OFFER,
			PolicyType.DREAM_OFFER, PolicyType.ONE_OFFER);

	private final PlacementPolicyRepository policyRepository;
	private final UserRepository userRepository;
	private final CurrentUserProvider currentUserProvider;
	private final PolicyMapper policyMapper;
	private final AuditService auditService;
	private final Clock clock;

	public PolicyService(PlacementPolicyRepository policyRepository, UserRepository userRepository,
			CurrentUserProvider currentUserProvider, PolicyMapper policyMapper, AuditService auditService,
			Clock clock) {
		this.policyRepository = policyRepository;
		this.userRepository = userRepository;
		this.currentUserProvider = currentUserProvider;
		this.policyMapper = policyMapper;
		this.auditService = auditService;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public List<PolicyResponse> list() {
		return policyRepository.findAllByOrderByIdAsc().stream().map(policyMapper::toResponse).toList();
	}

	@Transactional
	public PolicyResponse update(PolicyType type, PolicyUpdateRequest request) {
		checkSettings(type, request);

		// Lock every policy row until commit, so the conflict check below can't race with another admin
		Map<PolicyType, PlacementPolicy> byType = policyRepository.findAllForUpdate().stream()
				.collect(Collectors.toMap(PlacementPolicy::getType, Function.identity(), (a, b) -> a,
						() -> new EnumMap<>(PolicyType.class)));
		PlacementPolicy policy = byType.get(type);
		if (policy == null) {
			throw new ResourceNotFoundException("Policy " + type + " not found");
		}
		if (request.active()) {
			checkNoConflictingPolicyIsActive(type, byType);
		}

		Map<String, Object> oldValue = auditValue(policy);
		policy.setActive(request.active());
		// @Digits allows at most 2 decimals, so setScale only pads ("10" -> "10.00"), never rounds
		policy.setCtcThresholdLpa(request.ctcThresholdLpa() == null
				? null
				: request.ctcThresholdLpa().setScale(2, RoundingMode.UNNECESSARY));
		policy.setMaxOffers(request.maxOffers() == null ? null : request.maxOffers().shortValue());
		// getReferenceById gives a proxy for the FK without an extra SELECT
		policy.setUpdatedBy(userRepository.getReferenceById(currentUserProvider.currentUserId()));
		policy.setUpdatedAt(Instant.now(clock));
		Map<String, Object> newValue = auditValue(policy);
		if (!newValue.equals(oldValue)) {
			auditService.record(AuditEntityType.POLICY, policy.getId(), AuditAction.POLICY_CHANGED, oldValue,
					newValue);
		}
		return policyMapper.toResponse(policy);
	}

	/**
	 * The settings that matter for the audit. The threshold is always stored with 2 decimals,
	 * so 10.00 equals 10.00 here (BigDecimal.equals compares the scale too).
	 */
	private static Map<String, Object> auditValue(PlacementPolicy policy) {
		Map<String, Object> value = new LinkedHashMap<>(); // settings may be null, so not Map.of
		value.put("type", policy.getType().name());
		value.put("active", policy.isActive());
		value.put("ctcThresholdLpa", policy.getCtcThresholdLpa());
		value.put("maxOffers", policy.getMaxOffers());
		return value;
	}

	/** Each type needs exactly its own setting; a setting that doesn't apply is rejected, not ignored. */
	private static void checkSettings(PolicyType type, PolicyUpdateRequest request) {
		boolean hasThreshold = request.ctcThresholdLpa() != null;
		boolean hasMaxOffers = request.maxOffers() != null;
		switch (type) {
			case ONE_OFFER -> {
				rejectIf(hasThreshold, "ctcThresholdLpa", type);
				rejectIf(hasMaxOffers, "maxOffers", type);
			}
			case DREAM_OFFER -> {
				if (!hasThreshold) {
					throw new BadRequestException("DREAM_OFFER requires ctcThresholdLpa (greater than 0)");
				}
				rejectIf(hasMaxOffers, "maxOffers", type);
			}
			case MAX_OFFERS -> {
				if (!hasMaxOffers) {
					throw new BadRequestException("MAX_OFFERS requires maxOffers (1 or more)");
				}
				rejectIf(hasThreshold, "ctcThresholdLpa", type);
			}
		}
	}

	private static void rejectIf(boolean present, String field, PolicyType type) {
		if (present) {
			throw new BadRequestException(field + " does not apply to " + type + "; leave it out");
		}
	}

	private static void checkNoConflictingPolicyIsActive(PolicyType type, Map<PolicyType, PlacementPolicy> byType) {
		PolicyType conflicting = CONFLICTING.get(type);
		if (conflicting == null) {
			return;
		}
		PlacementPolicy other = byType.get(conflicting);
		if (other != null && other.isActive()) {
			throw new ConflictException("Cannot activate " + type + " while " + conflicting + " is active: "
					+ "the two policies contradict each other. Deactivate " + conflicting + " first");
		}
	}

}
