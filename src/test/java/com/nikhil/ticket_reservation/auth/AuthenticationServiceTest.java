package com.nikhil.ticket_reservation.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import com.nikhil.ticket_reservation.exception.ApiException;

class AuthenticationServiceTest {

	private final AuthenticationService authenticationService = new AuthenticationService("admin-secret");

	@Test
	void acceptsConfiguredAdminBearerToken() {
		authenticationService.requireAdmin("Bearer admin-secret");
	}

	@Test
	void extractsUserIdFromBearerToken() {
		assertEquals("alice", authenticationService.requireUser("Bearer user:alice"));
	}

	@Test
	void rejectsUserTokenForAdminAccess() {
		ApiException exception = assertThrows(ApiException.class,
				() -> authenticationService.requireAdmin("Bearer user:alice"));
		assertEquals("unauthorized_action", exception.error());
	}
}
