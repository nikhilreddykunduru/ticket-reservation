package com.nikhil.ticket_reservation.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.servlet.resource.NoResourceFoundException;

class ApiExceptionHandlerTest {

	private final ApiExceptionHandler handler = new ApiExceptionHandler();

	@Test
	void returnsGenericInternalErrorForUnexpectedFailures() {
		var response = handler.handleUnexpectedException(new IllegalStateException("internal detail"));

		assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
		assertEquals("internal_server_error", response.getBody().error());
	}

	@Test
	void returnsNotFoundForMissingResources() {
		var exception = mock(NoResourceFoundException.class);
		var response = handler.handleNoResourceFound(exception);

		assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
		assertEquals("resource_not_found", response.getBody().error());
	}
}
