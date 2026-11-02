package com.placementhub.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.placementhub.common.Branch;
import com.placementhub.common.ResourceNotFoundException;

/** HTTP details of the export. Security is tested in SecurityAccessTest, so filters are off here. */
@WebMvcTest(ApplicantController.class)
@AutoConfigureMockMvc(addFilters = false)
class ApplicantControllerTest {

	@Autowired
	private MockMvc mvc;
	@MockitoBean
	private ApplicantService applicantService;

	@Test
	void exportIsAUtf8CsvAttachmentNamedAfterTheDrive() throws Exception {
		when(applicantService.listAll(eq(7L), any())).thenReturn(List.of());

		MvcResult result = mvc.perform(get("/api/drives/7/applications/export"))
				.andExpect(status().isOk())
				.andExpect(header().string("Content-Type", "text/csv;charset=UTF-8"))
				.andExpect(header().string("Content-Disposition",
						"attachment; filename=\"drive-7-applicants.csv\""))
				.andReturn();

		assertThat(result.getResponse().getContentAsString(StandardCharsets.UTF_8)).startsWith("﻿Application ID,");
	}

	@Test
	void exportPassesTheFiltersOn() throws Exception {
		when(applicantService.listAll(eq(7L), any())).thenReturn(List.of());

		mvc.perform(get("/api/drives/7/applications/export?status=APPLIED&branch=IT&minCgpa=7.5"))
				.andExpect(status().isOk());

		verify(applicantService).listAll(7L,
				new ApplicantFilter(ApplicationStatus.APPLIED, Branch.IT, new BigDecimal("7.5")));
	}

	@Test
	void anUnknownDriveIsAJson404EvenWhenTheClientAsksForCsv() throws Exception {
		when(applicantService.listAll(eq(99L), any())).thenThrow(ResourceNotFoundException.of("Drive", 99L));

		mvc.perform(get("/api/drives/99/applications/export"))
				.andExpect(status().isNotFound())
				.andExpect(content().contentTypeCompatibleWith("application/json"))
				.andExpect(jsonPath("$.status").value(404));
	}

	@Test
	void anUnknownStatusIs400() throws Exception {
		mvc.perform(get("/api/drives/7/applications?status=HIRED"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.status").value(400));
	}

}
