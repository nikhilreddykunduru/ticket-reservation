package com.nikhil.ticket_reservation.repository;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.nikhil.ticket_reservation.model.Seat;

@Repository
public class ReservationRepository {

	private final JdbcTemplate jdbcTemplate;

	public ReservationRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	public int createAndLockUserShowCounter(UUID showId, String userId) {
		jdbcTemplate.update(
				"INSERT INTO user_show_counters (show_id, user_id, occupied_count) VALUES (?, ?, 0) "
						+ "ON CONFLICT (show_id, user_id) DO NOTHING",
				showId, userId);
		return jdbcTemplate.queryForObject(
				"SELECT occupied_count FROM user_show_counters WHERE show_id = ? AND user_id = ? FOR UPDATE",
				Integer.class, showId, userId);
	}

	public List<Seat> lockSeatsByCode(UUID showId, List<String> seatCodes) {
		String placeholders = String.join(",", java.util.Collections.nCopies(seatCodes.size(), "?"));
		List<Object> arguments = new ArrayList<>();
		arguments.add(showId);
		arguments.addAll(seatCodes);
		return jdbcTemplate.query(
				"SELECT id, show_id, seat_code, status, reservation_id FROM seats "
						+ "WHERE show_id = ? AND seat_code IN (" + placeholders + ") "
						+ "ORDER BY seat_code FOR UPDATE",
				(resultSet, rowNumber) -> new Seat(
						resultSet.getObject("id", UUID.class),
						resultSet.getObject("show_id", UUID.class),
						resultSet.getString("seat_code"),
						resultSet.getString("status"),
						resultSet.getObject("reservation_id", UUID.class)),
				arguments.toArray());
	}

	public void createReservation(UUID reservationId, UUID showId, String userId, long amountPaise) {
		jdbcTemplate.update(
				"INSERT INTO reservations (id, show_id, user_id, amount_paise, status) "
						+ "VALUES (?, ?, ?, ?, 'CONFIRMED')",
				reservationId, showId, userId, amountPaise);
	}

	public void createReservationSeats(UUID reservationId, List<Seat> seats) {
		jdbcTemplate.batchUpdate(
				"INSERT INTO reservation_seats (reservation_id, seat_id) VALUES (?, ?)",
				seats,
				seats.size(),
				(statement, seat) -> {
					statement.setObject(1, reservationId);
					statement.setObject(2, seat.id());
				});
	}

	public void confirmSeats(UUID reservationId, List<Seat> seats) {
		String placeholders = String.join(",", java.util.Collections.nCopies(seats.size(), "?"));
		List<Object> arguments = new ArrayList<>();
		arguments.add(reservationId);
		seats.forEach(seat -> arguments.add(seat.id()));
		int updated = jdbcTemplate.update(
				"UPDATE seats SET status = 'CONFIRMED', reservation_id = ? "
						+ "WHERE status = 'AVAILABLE' AND id IN (" + placeholders + ")",
				arguments.toArray());
		if (updated != seats.size()) {
			throw new IllegalStateException("Locked seats changed before confirmation");
		}
	}

	public void incrementOccupiedCount(UUID showId, String userId, int seats) {
		jdbcTemplate.update(
				"UPDATE user_show_counters SET occupied_count = occupied_count + ? "
						+ "WHERE show_id = ? AND user_id = ?",
				seats, showId, userId);
	}

}
