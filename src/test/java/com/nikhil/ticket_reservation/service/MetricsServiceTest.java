package com.nikhil.ticket_reservation.service;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class MetricsServiceTest {

	@Test
	void scrapesCountersAndReadsAvailableSeatsFromDatabase() {
		JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
		when(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM seats WHERE status = 'AVAILABLE'", Long.class))
				.thenReturn(12L);
		SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
		meterRegistry.counter("reservations_confirmed_total").increment(2);
		meterRegistry.counter("reservations_declined_total", "reason", "seat_taken").increment();
		meterRegistry.counter("reservations_replayed_total").increment(3);
		MetricsService service = new MetricsService(jdbcTemplate, meterRegistry);

		String result = service.scrape();

		assertTrue(result.contains("reservations_confirmed_total 2"));
		assertTrue(result.contains("reservations_declined_total{reason=\"seat_taken\"} 1"));
		assertTrue(result.contains("reservations_declined_total{reason=\"per_user_limit\"} 0"));
		assertTrue(result.contains("reservations_replayed_total 3"));
		assertTrue(result.contains("seats_available 12"));
		verify(jdbcTemplate).queryForObject("SELECT COUNT(*) FROM seats WHERE status = 'AVAILABLE'", Long.class);
	}
}
