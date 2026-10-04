package com.nikhil.ticket_reservation.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.nikhil.ticket_reservation.model.Seat;
import com.nikhil.ticket_reservation.model.Show;

@Repository
public class ShowRepository {

	private final JdbcTemplate jdbcTemplate;

	public ShowRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	public void create(Show show) {
		jdbcTemplate.update(
				"INSERT INTO shows (id, name, price_paise, per_user_limit) VALUES (?, ?, ?, ?)",
				show.id(), show.name(), show.pricePaise(), show.perUserLimit());
	}

	public Optional<Show> findById(UUID id) {
		List<Show> shows = jdbcTemplate.query(
				"SELECT id, name, price_paise, per_user_limit FROM shows WHERE id = ?",
				(resultSet, rowNumber) -> new Show(
						resultSet.getObject("id", UUID.class),
						resultSet.getString("name"),
						resultSet.getLong("price_paise"),
						resultSet.getInt("per_user_limit")),
				id);
		return shows.stream().findFirst();
	}

	public List<Seat> findSeatsByShowId(UUID showId) {
		return jdbcTemplate.query(
				"SELECT id, show_id, seat_code, status, reservation_id FROM seats WHERE show_id = ? ORDER BY seat_code",
				(resultSet, rowNumber) -> new Seat(
						resultSet.getObject("id", UUID.class),
						resultSet.getObject("show_id", UUID.class),
						resultSet.getString("seat_code"),
						resultSet.getString("status"),
						resultSet.getObject("reservation_id", UUID.class)),
				showId);
	}
}
