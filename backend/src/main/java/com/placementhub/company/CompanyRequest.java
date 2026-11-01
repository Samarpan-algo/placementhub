package com.placementhub.company;

import org.hibernate.validator.constraints.URL;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Body for creating a company and for replacing one with PUT. */
public record CompanyRequest(

		@NotBlank
		@Size(max = 150)
		String name,

		@Size(max = 255)
		@URL
		String website,

		@Size(max = 2000)
		String description) {
}
