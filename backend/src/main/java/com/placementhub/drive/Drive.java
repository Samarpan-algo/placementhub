package com.placementhub.drive;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.Set;

import com.placementhub.common.Branch;
import com.placementhub.company.Company;
import com.placementhub.user.User;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
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

@Entity
@Table(name = "drives")
public class Drive {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "company_id", nullable = false)
	private Company company;

	@Column(name = "role_title", nullable = false, length = 150)
	private String roleTitle;

	@Enumerated(EnumType.STRING)
	@Column(name = "job_type", nullable = false, length = 20)
	private JobType jobType;

	@Column(name = "ctc_lpa", nullable = false, precision = 6, scale = 2)
	private BigDecimal ctcLpa;

	@Column(length = 150)
	private String location;

	@Column(columnDefinition = "text")
	private String description;

	@Column(name = "min_cgpa", nullable = false, precision = 4, scale = 2)
	private BigDecimal minCgpa;

	// SMALLINT columns map to Short so that ddl-auto=validate passes
	@Column(name = "max_backlogs", nullable = false)
	private Short maxBacklogs;

	@Column(name = "graduation_year", nullable = false)
	private Short graduationYear;

	@Column(name = "registration_deadline", nullable = false)
	private Instant registrationDeadline;

	@Column(name = "drive_date")
	private LocalDate driveDate;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private DriveStatus status = DriveStatus.DRAFT;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "created_by", nullable = false)
	private User createdBy;

	@ElementCollection(fetch = FetchType.LAZY)
	@CollectionTable(name = "drive_branches", joinColumns = @JoinColumn(name = "drive_id"))
	@Enumerated(EnumType.STRING)
	@Column(name = "branch", nullable = false, length = 10)
	private Set<Branch> branches = EnumSet.noneOf(Branch.class);

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected Drive() {
		// for JPA
	}

	public Drive(Company company, User createdBy) {
		this.company = company;
		this.createdBy = createdBy;
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

	public Company getCompany() {
		return company;
	}

	public void setCompany(Company company) {
		this.company = company;
	}

	public String getRoleTitle() {
		return roleTitle;
	}

	public void setRoleTitle(String roleTitle) {
		this.roleTitle = roleTitle;
	}

	public JobType getJobType() {
		return jobType;
	}

	public void setJobType(JobType jobType) {
		this.jobType = jobType;
	}

	public BigDecimal getCtcLpa() {
		return ctcLpa;
	}

	public void setCtcLpa(BigDecimal ctcLpa) {
		this.ctcLpa = ctcLpa;
	}

	public String getLocation() {
		return location;
	}

	public void setLocation(String location) {
		this.location = location;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public BigDecimal getMinCgpa() {
		return minCgpa;
	}

	public void setMinCgpa(BigDecimal minCgpa) {
		this.minCgpa = minCgpa;
	}

	public Short getMaxBacklogs() {
		return maxBacklogs;
	}

	public void setMaxBacklogs(Short maxBacklogs) {
		this.maxBacklogs = maxBacklogs;
	}

	public Short getGraduationYear() {
		return graduationYear;
	}

	public void setGraduationYear(Short graduationYear) {
		this.graduationYear = graduationYear;
	}

	public Instant getRegistrationDeadline() {
		return registrationDeadline;
	}

	public void setRegistrationDeadline(Instant registrationDeadline) {
		this.registrationDeadline = registrationDeadline;
	}

	public LocalDate getDriveDate() {
		return driveDate;
	}

	public void setDriveDate(LocalDate driveDate) {
		this.driveDate = driveDate;
	}

	public DriveStatus getStatus() {
		return status;
	}

	public void setStatus(DriveStatus status) {
		this.status = status;
	}

	public User getCreatedBy() {
		return createdBy;
	}

	public Set<Branch> getBranches() {
		return branches;
	}

	/** Replaces the branch list in place, so Hibernate tracks the change on the managed collection. */
	public void setBranches(Set<Branch> newBranches) {
		branches.clear();
		branches.addAll(newBranches);
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

}
