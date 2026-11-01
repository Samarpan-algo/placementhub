package com.placementhub.student;

import java.math.BigDecimal;
import java.time.Instant;

import com.placementhub.common.Branch;
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
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

/**
 * A student's academic profile. Academic fields (roll no, branch, graduation year, CGPA, backlogs)
 * have no setters: students cannot change them. Admin-side updates come in a later milestone.
 */
@Entity
@Table(name = "student_profiles")
public class StudentProfile {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@OneToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false, unique = true)
	private User user;

	@Column(name = "full_name", nullable = false, length = 100)
	private String fullName;

	@Column(name = "roll_no", nullable = false, unique = true, length = 30)
	private String rollNo;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 10)
	private Branch branch;

	// SMALLINT columns map to Short so that ddl-auto=validate passes
	@Column(name = "graduation_year", nullable = false)
	private Short graduationYear;

	@Column(nullable = false, precision = 4, scale = 2)
	private BigDecimal cgpa;

	@Column(name = "active_backlogs", nullable = false)
	private Short activeBacklogs;

	@Column(length = 15)
	private String phone;

	@Column(name = "resume_url", columnDefinition = "text")
	private String resumeUrl;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected StudentProfile() {
		// for JPA
	}

	public StudentProfile(User user, String fullName, String rollNo, Branch branch, short graduationYear,
			BigDecimal cgpa, short activeBacklogs) {
		this.user = user;
		this.fullName = fullName;
		this.rollNo = rollNo;
		this.branch = branch;
		this.graduationYear = graduationYear;
		this.cgpa = cgpa;
		this.activeBacklogs = activeBacklogs;
	}

	@PrePersist
	void onCreate() {
		createdAt = Instant.now();
		updatedAt = createdAt;
	}

	@PreUpdate
	void onUpdate() {
		updatedAt = Instant.now();
	}

	public Long getId() {
		return id;
	}

	public User getUser() {
		return user;
	}

	public String getFullName() {
		return fullName;
	}

	public void setFullName(String fullName) {
		this.fullName = fullName;
	}

	public String getRollNo() {
		return rollNo;
	}

	public Branch getBranch() {
		return branch;
	}

	public Short getGraduationYear() {
		return graduationYear;
	}

	public BigDecimal getCgpa() {
		return cgpa;
	}

	public Short getActiveBacklogs() {
		return activeBacklogs;
	}

	public String getPhone() {
		return phone;
	}

	public void setPhone(String phone) {
		this.phone = phone;
	}

	public String getResumeUrl() {
		return resumeUrl;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

}
