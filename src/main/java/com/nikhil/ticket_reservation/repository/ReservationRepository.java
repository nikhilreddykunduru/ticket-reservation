package com.nikhil.ticket_reservation.repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.nikhil.ticket_reservation.dto.ReservationResponse;
import com.nikhil.ticket_reservation.model.Seat;

@Repository
public class ReservationRepository {

	private final JdbcTemplate jdbcTemplate;

	public ReservationRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	public Optional<String> findIdempotencyRequestHash(UUID showId, String userId, String key) {
		List<String> hashes = jdbcTemplate.query(
				"SELECT request_hash FROM idempotency_keys WHERE show_id = ? AND user_id = ? AND idempotency_key = ?",
				(resultSet, rowNumber) -> resultSet.getString("request_hash"),
				showId, userId, key);
		return hashes.stream().findFirst();
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

	public void createIdempotencyRecord(
			UUID recordId, UUID showId, String userId, String key, String requestHash, UUID reservationId) {
		jdbcTemplate.update(
				"INSERT INTO idempotency_keys "
						+ "(id, show_id, user_id, idempotency_key, request_hash, reservation_id) "
						+ "VALUES (?, ?, ?, ?, ?, ?)",
				recordId, showId, userId, key, requestHash, reservationId);
	}

	public Optional<ReservationResponse> findReservation(UUID showId, String userId, String key) {
		List<ReservationResponse> reservations = jdbcTemplate.query(
				"SELECT r.id, r.show_id, r.user_id, r.amount_paise, r.status, s.seat_code "
						+ "FROM idempotency_keys i "
						+ "JOIN reservations r ON r.id = i.reservation_id "
						+ "JOIN reservation_seats rs ON rs.reservation_id = r.id "
						+ "JOIN seats s ON s.id = rs.seat_id "
						+ "WHERE i.show_id = ? AND i.user_id = ? AND i.idempotency_key = ? "
						+ "ORDER BY s.seat_code",
				(resultSet, rowNumber) -> new ReservationResponse(
						resultSet.getObject("id", UUID.class),
						resultSet.getObject("show_id", UUID.class),
						resultSet.getString("user_id"),
						List.of(resultSet.getString("seat_code")),
						resultSet.getLong("amount_paise"),
						resultSet.getString("status").toLowerCase(java.util.Locale.ROOT)),
				showId, userId, key);
		if (reservations.isEmpty()) {
			return Optional.empty();
		}
		ReservationResponse first = reservations.getFirst();
		return Optional.of(new ReservationResponse(
				first.reservationId(),
				first.showId(),
				first.userId(),
				reservations.stream().flatMap(reservation -> reservation.seats().stream()).toList(),
				first.amountPaise(),
				first.status()));
	}
}
