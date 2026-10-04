package com.nikhil.ticket_reservation.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

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
}
