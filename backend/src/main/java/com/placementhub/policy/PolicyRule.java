package com.placementhub.policy;

import java.util.List;

import com.placementhub.drive.Drive;

/**
 * One placement policy (Strategy pattern). Each rule is a stateless Spring bean; PolicyEngine
 * picks the right one by type() for every active policy row.
 */
public interface PolicyRule {

	/** The placement_policies row that configures this rule. */
	PolicyType type();

	/**
	 * @param settings the admin's settings for this rule (threshold, limit)
	 * @param drive    the FULL_TIME drive the student wants to apply to
	 * @param offers   the student's FULL_TIME offers (already filtered by PolicyEngine)
	 */
	PolicyResult evaluate(PolicySettings settings, Drive drive, List<Offer> offers);

}
