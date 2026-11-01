package com.placementhub.company;

import org.springframework.stereotype.Component;

/** Hand-written mapping between Company entities and DTOs. Normalises text input (trim, blank → null). */
@Component
public class CompanyMapper {

	public Company toEntity(CompanyRequest request) {
		return new Company(request.name().trim(), blankToNull(request.website()), blankToNull(request.description()));
	}

	public void updateEntity(Company company, CompanyRequest request) {
		company.setName(request.name().trim());
		company.setWebsite(blankToNull(request.website()));
		company.setDescription(blankToNull(request.description()));
	}

	public CompanyResponse toResponse(Company company) {
		return new CompanyResponse(company.getId(), company.getName(), company.getWebsite(),
				company.getDescription(), company.getCreatedAt());
	}

	private static String blankToNull(String value) {
		return (value == null || value.isBlank()) ? null : value.trim();
	}

}
