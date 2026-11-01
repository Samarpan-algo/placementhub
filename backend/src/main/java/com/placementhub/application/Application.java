package com.placementhub.application;

import java.time.Instant;

import com.placementhub.drive.Drive;
import com.placementhub.student.StudentProfile;

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

/** One student's application to one drive. UNIQUE (drive_id, student_id) in the DB. */
@Entity
@Table(name = "applications")
public class Application {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "drive_id", nullable = false)
	private Drive drive;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "student_id", nullable = false)
	private StudentProfile student;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private ApplicationStatus status = ApplicationStatus.APPLIED;

	@Column(name = "status_reason", length = 300)
	private String statusReason;

	@Column(name = "applied_at", nullable = false, updatable = false)
	private Instant appliedAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected Application() {
		// for JPA
	}

	public Application(Drive drive, StudentProfile student) {
		this.drive = drive;
		this.student = student;
	}

	@PrePersist
	void onCreate() {
		appliedAt = Instant.now();
		updatedAt = appliedAt;
	}

	@PreUpdate
	void onUpdate() {
		updatedAt = Instant.now();
	}

	public Long getId() {
		return id;
	}

	public Drive getDrive() {
		return drive;
	}

	public StudentProfile getStudent() {
		return student;
	}

	public ApplicationStatus getStatus() {
		return status;
	}

	public void setStatus(ApplicationStatus status) {
		this.status = status;
	}

	public String getStatusReason() {
		return statusReason;
	}

	public void setStatusReason(String statusReason) {
		this.statusReason = statusReason;
	}

	public Instant getAppliedAt() {
		return appliedAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

}
