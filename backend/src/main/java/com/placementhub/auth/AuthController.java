package com.placementhub.auth;

import java.net.URI;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.placementhub.student.StudentProfileResponse;

import jakarta.validation.Valid;

/** Public endpoints (see SecurityConfig): student registration and login. */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

	private final AuthService authService;

	public AuthController(AuthService authService) {
		this.authService = authService;
	}

	/** 201 + the new profile. No token: the student logs in next. */
	@PostMapping("/register")
	public ResponseEntity<StudentProfileResponse> register(@Valid @RequestBody RegisterRequest request) {
		StudentProfileResponse profile = authService.register(request);
		return ResponseEntity.created(URI.create("/api/students/me")).body(profile);
	}

	@PostMapping("/login")
	public LoginResponse login(@Valid @RequestBody LoginRequest request) {
		return authService.login(request);
	}

}
