package com.placementhub.drive;

import org.springframework.data.jpa.repository.JpaRepository;

public interface DriveRepository extends JpaRepository<Drive, Long> {

	long countByCompanyId(Long companyId);

}
