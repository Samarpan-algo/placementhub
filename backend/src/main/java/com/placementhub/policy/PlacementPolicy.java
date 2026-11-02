package com.placementhub.policy;

import java.math.BigDecimal;
import java.time.Instant;

import com.placementhub.user.User;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * One configurable placement policy. Exactly one row per type (seeded in V2); admins
 * only update rows, never create or delete them.
 */
@Entity
@Table(name = "placement_policies")
public class PlacementPolicy {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, unique = true, updatable = false, length = 20)
	private PolicyType type;

	/** DREAM_OFFER only: the CTC (in LPA) that counts as a dream offer. */
	@Column(name = "ctc_threshold_lpa", precision = 6, scale = 2)
	private BigDecimal ctcThresholdLpa;

	/** MAX_OFFERS only: the most offers a student may hold. */
	@Column(name = "max_offers")
	private Short maxOffers;

	@Column(nullable = false)
	private boolean active;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "updated_by")
	private User updatedBy;

	/** Set by PolicyService from its Clock on every save, so even a no-change PUT records who saved last. */
	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected PlacementPolicy() {
		// for JPA
	}

	public PlacementPolicy(PolicyType type) {
		this.type = type;
	}

	public Long getId() {
		return id;
	}

	public PolicyType getType() {
		return type;
	}

	public BigDecimal getCtcThresholdLpa() {
		return ctcThresholdLpa;
	}

	public void setCtcThresholdLpa(BigDecimal ctcThresholdLpa) {
		this.ctcThresholdLpa = ctcThresholdLpa;
	}

	public Short getMaxOffers() {
		return maxOffers;
	}

	public void setMaxOffers(Short maxOffers) {
		this.maxOffers = maxOffers;
	}

	public boolean isActive() {
		return active;
	}

	public void setActive(boolean active) {
		this.active = active;
	}

	public User getUpdatedBy() {
		return updatedBy;
	}

	public void setUpdatedBy(User updatedBy) {
		this.updatedBy = updatedBy;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

	public void setUpdatedAt(Instant updatedAt) {
		this.updatedAt = updatedAt;
	}

}
