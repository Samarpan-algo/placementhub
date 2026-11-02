package com.placementhub.policy;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import jakarta.persistence.LockModeType;

public interface PlacementPolicyRepository extends JpaRepository<PlacementPolicy, Long> {

	List<PlacementPolicy> findByActiveTrue();

	List<PlacementPolicy> findAllByOrderByIdAsc();

	/**
	 * SELECT ... FOR UPDATE on every policy row, until the transaction ends. Used when an admin
	 * changes a policy, so two admins can't activate ONE_OFFER and DREAM_OFFER at the same moment.
	 * ORDER BY id makes every transaction lock the rows in the same order, so they can't deadlock.
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("SELECT p FROM PlacementPolicy p ORDER BY p.id")
	List<PlacementPolicy> findAllForUpdate();

}
