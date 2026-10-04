package com.nikhil.ticket_reservation.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;

class HealthControllerTest {

	@Test
	void reportsReadyWhenDatabaseQuerySucceeds() {
		JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
		when(jdbcTemplate.queryForObject("SELECT 1", Integer.class)).thenReturn(1);
		HealthController controller = new HealthController(jdbcTemplate);

		var response = controller.ready();

		assertEquals(HttpStatus.OK, response.getStatusCode());
		assertEquals("ok", response.getBody().get("status"));
		verify(jdbcTemplate).queryForObject("SELECT 1", Integer.class);
	}

	@Test
	void reportsUnavailableWhenDatabaseQueryFails() {
		JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
		when(jdbcTemplate.queryForObject("SELECT 1", Integer.class))
				.thenThrow(new DataAccessResourceFailureException("Database unavailable"));
		HealthController controller = new HealthController(jdbcTemplate);

		var response = controller.ready();

		assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
		assertEquals("unavailable", response.getBody().get("status"));
		verify(jdbcTemplate).queryForObject("SELECT 1", Integer.class);
	}
}
