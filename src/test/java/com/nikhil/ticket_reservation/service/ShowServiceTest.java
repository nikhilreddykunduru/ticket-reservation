package com.nikhil.ticket_reservation.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;

import com.nikhil.ticket_reservation.dto.CreateShowRequest;
import com.nikhil.ticket_reservation.exception.ApiException;
import com.nikhil.ticket_reservation.repository.ShowRepository;

class ShowServiceTest {

	private final ShowRepository showRepository = mock(ShowRepository.class);
	private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
	private final ShowService showService = new ShowService(showRepository, jdbcTemplate);

	@Test
	void rejectsDuplicateSeatNamesBeforeWriting() {
		CreateShowRequest request = new CreateShowRequest("friday-night",
				java.util.List.of("A1", "A1"), 25000L, 4);

		ApiException exception = assertThrows(ApiException.class,
				() -> showService.createShow(request));

		assertEquals(HttpStatus.BAD_REQUEST, exception.status());
		assertEquals("invalid_request", exception.error());
		verifyNoInteractions(showRepository, jdbcTemplate);
	}
}
