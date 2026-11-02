package com.placementhub.round;

import java.time.Instant;

import com.placementhub.drive.Drive;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * One selection round of a drive (e.g. Round 1: Aptitude). Numbered 1, 2, 3… with no gaps,
 * UNIQUE (drive_id, round_no) in the DB. The round number never changes after creation.
 */
@Entity
@Table(name = "drive_rounds")
public class DriveRound {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "drive_id", nullable = false, updatable = false)
	private Drive drive;

	// SMALLINT columns map to Short so that ddl-auto=validate passes
	@Column(name = "round_no", nullable = false, updatable = false)
	private Short roundNo;

	@Column(nullable = false, length = 100)
	private String name;

	@Column(name = "scheduled_at")
	private Instant scheduledAt;

	protected DriveRound() {
		// for JPA
	}

	public DriveRound(Drive drive, short roundNo) {
		this.drive = drive;
		this.roundNo = roundNo;
	}

	/** "Round 2: Technical", used in rejection reasons and error messages. */
	public String label() {
		return "Round " + roundNo + ": " + name;
	}

	public Long getId() {
		return id;
	}

	public Drive getDrive() {
		return drive;
	}

	public Short getRoundNo() {
		return roundNo;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public Instant getScheduledAt() {
		return scheduledAt;
	}

	public void setScheduledAt(Instant scheduledAt) {
		this.scheduledAt = scheduledAt;
	}

}
