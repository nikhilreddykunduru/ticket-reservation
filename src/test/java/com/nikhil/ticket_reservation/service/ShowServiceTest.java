package com.nikhil.ticket_reservation.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

import com.nikhil.ticket_reservation.dto.CreateShowRequest;
import com.nikhil.ticket_reservation.repository.ShowRepository;

class ShowServiceTest {

	private final ShowRepository showRepository = mock(ShowRepository.class);
	private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
	private final ShowService showService = new ShowService(showRepository, jdbcTemplate);

	@Test
	void rejectsDuplicateSeatNamesBeforeWriting() {
		CreateShowRequest request = new CreateShowRequest("friday-night",
				java.util.List.of("A1", "A1"), 25000L, 4);

		ResponseStatusException exception = assertThrows(ResponseStatusException.class,
				() -> showService.createShow(request));

		assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
		verifyNoInteractions(showRepository, jdbcTemplate);
	}
}
