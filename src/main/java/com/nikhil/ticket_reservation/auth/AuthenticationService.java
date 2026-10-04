package com.nikhil.ticket_reservation.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.nikhil.ticket_reservation.exception.ApiException;

@Service
public class AuthenticationService {

	private static final String BEARER_PREFIX = "Bearer ";

	private final String adminToken;

	public AuthenticationService(@Value("${ticket-reservation.admin-token}") String adminToken) {
		this.adminToken = adminToken;
	}

	public void requireAdmin(String authorizationHeader) {
		String token = extractToken(authorizationHeader);
		if (!adminToken.equals(token)) {
			throw new ApiException(HttpStatus.FORBIDDEN, "unauthorized_action", "Admin token required");
		}
	}

	public String requireUser(String authorizationHeader) {
		String token = extractToken(authorizationHeader);
		if (!token.startsWith("user:") || token.length() == "user:".length()) {
			throw new ApiException(HttpStatus.FORBIDDEN, "unauthorized_action", "User token required");
		}
		return token.substring("user:".length());
	}

	private String extractToken(String authorizationHeader) {
		if (authorizationHeader == null || !authorizationHeader.startsWith(BEARER_PREFIX)) {
			throw new ApiException(HttpStatus.UNAUTHORIZED, "authentication_required", "Bearer token required");
		}

		String token = authorizationHeader.substring(BEARER_PREFIX.length());
		if (token.isBlank()) {
			throw new ApiException(HttpStatus.UNAUTHORIZED, "authentication_required", "Bearer token required");
		}
		return token;
	}
}
