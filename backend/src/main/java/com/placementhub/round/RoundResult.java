package com.placementhub.round;

import java.time.Instant;

import com.placementhub.application.Application;

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
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

/** One application's result in one round. UNIQUE (round_id, application_id): corrections update this row. */
@Entity
@Table(name = "round_results")
public class RoundResult {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "round_id", nullable = false, updatable = false)
	private DriveRound round;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "application_id", nullable = false, updatable = false)
	private Application application;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private RoundOutcome result;

	@Column(length = 500)
	private String remarks;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected RoundResult() {
		// for JPA
	}

	public RoundResult(DriveRound round, Application application) {
		this.round = round;
		this.application = application;
	}

	@PrePersist
	@PreUpdate
	void touch() {
		updatedAt = Instant.now();
	}

	public Long getId() {
		return id;
	}

	public DriveRound getRound() {
		return round;
	}

	public Application getApplication() {
		return application;
	}

	public RoundOutcome getResult() {
		return result;
	}

	public void setResult(RoundOutcome result) {
		this.result = result;
	}

	public String getRemarks() {
		return remarks;
	}

	public void setRemarks(String remarks) {
		this.remarks = remarks;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

}
