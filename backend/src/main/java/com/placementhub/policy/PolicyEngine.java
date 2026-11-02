package com.placementhub.policy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.placementhub.drive.Drive;
import com.placementhub.drive.JobType;

/**
 * Runs every active placement policy for one drive and collects every block reason.
 * Pure logic, like EligibilityService: callers load the active policies and the student's
 * offers once and pass them in, so checking many drives costs no extra queries.
 */
@Component
public class PolicyEngine {

	private final Map<PolicyType, PolicyRule> rulesByType;

	/** Spring injects every PolicyRule bean. Fails at startup if a type has no rule, or two. */
	public PolicyEngine(List<PolicyRule> rules) {
		Map<PolicyType, PolicyRule> map = new EnumMap<>(PolicyType.class);
		for (PolicyRule rule : rules) {
			if (map.put(rule.type(), rule) != null) {
				throw new IllegalStateException("Two PolicyRule beans for " + rule.type());
			}
		}
		for (PolicyType type : PolicyType.values()) {
			if (!map.containsKey(type)) {
				throw new IllegalStateException("No PolicyRule bean for " + type);
			}
		}
		this.rulesByType = Collections.unmodifiableMap(map);
	}

	/** Policies restrict only FULL_TIME drives; internships are never blocked by them. */
	public boolean appliesTo(Drive drive) {
		return drive.getJobType() == JobType.FULL_TIME;
	}

	/**
	 * @param policies       the active policies (inactive ones are ignored anyway)
	 * @param drive          the drive the student wants to apply to
	 * @param selectedOffers every SELECTED application of the student; only FULL_TIME ones count as offers
	 */
	public PolicyEvaluation evaluate(List<PlacementPolicy> policies, Drive drive, List<Offer> selectedOffers) {
		if (!appliesTo(drive)) {
			return PolicyEvaluation.ALLOWED;
		}
		List<Offer> offers = selectedOffers.stream().filter(Offer::isFullTime).toList();

		List<String> reasons = new ArrayList<>();
		policies.stream()
				.filter(PlacementPolicy::isActive)
				.sorted(Comparator.comparing(PlacementPolicy::getType)) // fixed order: ONE, DREAM, MAX
				.forEach(policy -> {
					PolicyResult result = rulesByType.get(policy.getType())
							.evaluate(PolicySettings.from(policy), drive, offers);
					if (!result.allowed()) {
						reasons.add(result.reason());
					}
				});
		return PolicyEvaluation.of(reasons);
	}

}
