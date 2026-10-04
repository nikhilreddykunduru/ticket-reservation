package com.nikhil.ticket_reservation.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class ApiExceptionHandlerTest {

	private final ApiExceptionHandler handler = new ApiExceptionHandler();

	@Test
	void returnsGenericInternalErrorForUnexpectedFailures() {
		var response = handler.handleUnexpectedException(new IllegalStateException("internal detail"));

		assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
		assertEquals("internal_server_error", response.getBody().error());
	}
}
