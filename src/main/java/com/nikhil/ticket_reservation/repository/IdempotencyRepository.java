package com.nikhil.ticket_reservation.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.nikhil.ticket_reservation.dto.ReservationResponse;

@Repository
public class IdempotencyRepository {

	private final JdbcTemplate jdbcTemplate;

	public IdempotencyRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	public Optional<String> findRequestHash(UUID showId, String userId, String key) {
		List<String> hashes = jdbcTemplate.query(
				"SELECT request_hash FROM idempotency_keys WHERE show_id = ? AND user_id = ? AND idempotency_key = ?",
				(resultSet, rowNumber) -> resultSet.getString("request_hash"),
				showId, userId, key);
		return hashes.stream().findFirst();
	}

	public void createRecord(
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
