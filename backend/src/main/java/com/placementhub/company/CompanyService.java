package com.placementhub.company;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.placementhub.common.ConflictException;
import com.placementhub.common.PageResponse;
import com.placementhub.common.ResourceNotFoundException;
import com.placementhub.drive.DriveRepository;

/**
 * Business rules for companies. Entities are mapped to DTOs inside these
 * transactional methods, so controllers never see an entity.
 */
@Service
public class CompanyService {

	private final CompanyRepository companyRepository;
	private final DriveRepository driveRepository;
	private final CompanyMapper companyMapper;

	public CompanyService(CompanyRepository companyRepository, DriveRepository driveRepository,
			CompanyMapper companyMapper) {
		this.companyRepository = companyRepository;
		this.driveRepository = driveRepository;
		this.companyMapper = companyMapper;
	}

	@Transactional
	public CompanyResponse create(CompanyRequest request) {
		String name = request.name().trim();
		if (companyRepository.existsByNameIgnoreCase(name)) {
			throw duplicateName(name);
		}
		Company saved = companyRepository.save(companyMapper.toEntity(request));
		return companyMapper.toResponse(saved);
	}

	@Transactional(readOnly = true)
	public CompanyResponse getById(Long id) {
		return companyMapper.toResponse(findOrThrow(id));
	}

	@Transactional(readOnly = true)
	public PageResponse<CompanyResponse> list(Pageable pageable) {
		return PageResponse.from(companyRepository.findAll(pageable).map(companyMapper::toResponse));
	}

	@Transactional
	public CompanyResponse update(Long id, CompanyRequest request) {
		Company company = findOrThrow(id);
		String name = request.name().trim();
		if (companyRepository.existsByNameIgnoreCaseAndIdNot(name, id)) {
			throw duplicateName(name);
		}
		companyMapper.updateEntity(company, request);
		// flush now so a DB constraint error surfaces here, not after the method returns
		companyRepository.flush();
		return companyMapper.toResponse(company);
	}

	@Transactional
	public void delete(Long id) {
		Company company = findOrThrow(id);
		long driveCount = driveRepository.countByCompanyId(id);
		if (driveCount > 0) {
			throw new ConflictException("Cannot delete company '" + company.getName() + "': it has " + driveCount
					+ " drive(s). Close or remove its drives first.");
		}
		companyRepository.delete(company);
	}

	private Company findOrThrow(Long id) {
		return companyRepository.findById(id).orElseThrow(() -> ResourceNotFoundException.of("Company", id));
	}

	private static ConflictException duplicateName(String name) {
		return new ConflictException("A company named '" + name + "' already exists");
	}

}
