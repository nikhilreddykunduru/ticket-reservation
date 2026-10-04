package com.nikhil.ticket_reservation.service;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nikhil.ticket_reservation.dto.CreateShowRequest;
import com.nikhil.ticket_reservation.dto.CreateShowResponse;
import com.nikhil.ticket_reservation.dto.ShowDetailsResponse;
import com.nikhil.ticket_reservation.dto.ShowDetailsResponse.SeatDetails;
import com.nikhil.ticket_reservation.exception.ApiException;
import com.nikhil.ticket_reservation.model.Seat;
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
			throw new ApiException(HttpStatus.BAD_REQUEST, "invalid_request", "Seat names must be unique");
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

	@Transactional(readOnly = true)
	public ShowDetailsResponse getShow(UUID id) {
		Show show = showRepository.findById(id)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "resource_not_found", "Show not found"));
		List<Seat> showSeats = showRepository.findSeatsByShowId(id);
		List<SeatDetails> seats = showSeats.stream()
				.map(seat -> new SeatDetails(seat.seatCode(), seat.status().toLowerCase(Locale.ROOT)))
				.toList();
		int availableSeats = 0;
		int confirmedSeats = 0;
		for (Seat seat : showSeats) {
			switch (seat.status()) {
				case "AVAILABLE" -> availableSeats++;
				case "CONFIRMED" -> confirmedSeats++;
				default -> throw new IllegalStateException("Unsupported seat status: " + seat.status());
			}
		}

		return new ShowDetailsResponse(show.id(), show.name(), show.pricePaise(), showSeats.size(),
				availableSeats, 0, confirmedSeats, seats);
	}
}
