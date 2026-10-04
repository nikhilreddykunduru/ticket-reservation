package com.nikhil.ticket_reservation.service;

import java.util.HashSet;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.nikhil.ticket_reservation.dto.CreateShowRequest;
import com.nikhil.ticket_reservation.dto.CreateShowResponse;
import com.nikhil.ticket_reservation.model.Show;
import com.nikhil.ticket_reservation.repository.ShowRepository;

@Service
public class ShowService {

	private final ShowRepository showRepository;
	private final JdbcTemplate jdbcTemplate;

	public ShowService(ShowRepository showRepository, JdbcTemplate jdbcTemplate) {
		this.showRepository = showRepository;
		this.jdbcTemplate = jdbcTemplate;
	}

	@Transactional
	public CreateShowResponse createShow(CreateShowRequest request) {
		if (new HashSet<>(request.seats()).size() != request.seats().size()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Seat names must be unique");
		}

		UUID showId = UUID.randomUUID();
		Show show = new Show(showId, request.name(), request.pricePaise(), request.perUserLimit());
		showRepository.create(show);

		jdbcTemplate.batchUpdate(
				"INSERT INTO seats (id, show_id, seat_code, status) VALUES (?, ?, ?, 'AVAILABLE')",
				request.seats(),
				request.seats().size(),
				(statement, seatCode) -> {
					statement.setObject(1, UUID.randomUUID());
					statement.setObject(2, showId);
					statement.setString(3, seatCode);
				});

		return new CreateShowResponse(showId, show.name(), List.copyOf(request.seats()),
				show.pricePaise(), show.perUserLimit());
	}
}
