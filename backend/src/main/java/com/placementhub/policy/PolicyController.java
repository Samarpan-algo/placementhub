package com.placementhub.policy;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

/** TPO_ADMIN only (see SecurityConfig). There are always exactly 3 policies, so no paging. */
@RestController
@RequestMapping("/api/policies")
public class PolicyController {

	private final PolicyService policyService;

	public PolicyController(PolicyService policyService) {
		this.policyService = policyService;
	}

	@GetMapping
	public List<PolicyResponse> list() {
		return policyService.list();
	}

	@PutMapping("/{type}")
	public PolicyResponse update(@PathVariable PolicyType type, @Valid @RequestBody PolicyUpdateRequest request) {
		return policyService.update(type, request);
	}

}
