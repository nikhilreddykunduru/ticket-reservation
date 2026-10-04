package com.nikhil.ticket_reservation.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.nikhil.ticket_reservation.PostgresIntegrationTest;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ReservationIntegrationTest extends PostgresIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void reservationUsesAuthenticatedUserAndReconcilesPersistedSeatCounts() throws Exception {
		UUID showId = UUID.randomUUID();
		jdbcTemplate.update(
				"INSERT INTO shows (id, name, price_paise, per_user_limit) VALUES (?, ?, ?, ?)",
				showId, "reservation-integration-" + showId, 25000L, 4);
		for (String seatCode : new String[] { "A12", "A13" }) {
			jdbcTemplate.update(
					"INSERT INTO seats (id, show_id, seat_code, status) VALUES (?, ?, ?, 'AVAILABLE')",
					UUID.randomUUID(), showId, seatCode);
		}

		mockMvc.perform(post("/shows/{id}/reserve", showId)
				.header("Authorization", "Bearer user:alice")
				.header("Idempotency-Key", "reservation-integration")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"user_id\":\"bob\",\"seats\":[\"A12\"]}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.user_id").value("alice"))
				.andExpect(jsonPath("$.seats[0]").value("A12"));

		assertEquals("alice", jdbcTemplate.queryForObject(
				"SELECT user_id FROM reservations WHERE show_id = ?", String.class, showId));
		assertEquals("CONFIRMED", jdbcTemplate.queryForObject(
				"SELECT status FROM seats WHERE show_id = ? AND seat_code = 'A12'", String.class, showId));

		mockMvc.perform(get("/shows/{id}", showId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.total_seats").value(2))
				.andExpect(jsonPath("$.available_seats").value(1))
				.andExpect(jsonPath("$.held_seats").value(0))
				.andExpect(jsonPath("$.confirmed_seats").value(1));

		int totalSeats = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM seats WHERE show_id = ?", Integer.class, showId);
		int availableSeats = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM seats WHERE show_id = ? AND status = 'AVAILABLE'",
				Integer.class, showId);
		int confirmedSeats = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM seats WHERE show_id = ? AND status = 'CONFIRMED'",
				Integer.class, showId);
		assertEquals(totalSeats, availableSeats + confirmedSeats);
	}

	@Test
	void postgresUniqueSeatConstraintRejectsDuplicateSeatCodesForOneShow() {
		UUID showId = UUID.randomUUID();
		jdbcTemplate.update(
				"INSERT INTO shows (id, name, price_paise, per_user_limit) VALUES (?, ?, ?, ?)",
				showId, "unique-seat-" + showId, 25000L, 4);
		jdbcTemplate.update(
				"INSERT INTO seats (id, show_id, seat_code, status) VALUES (?, ?, ?, 'AVAILABLE')",
				UUID.randomUUID(), showId, "A12");

		org.junit.jupiter.api.Assertions.assertThrows(
				org.springframework.dao.DuplicateKeyException.class,
				() -> jdbcTemplate.update(
						"INSERT INTO seats (id, show_id, seat_code, status) VALUES (?, ?, ?, 'AVAILABLE')",
						UUID.randomUUID(), showId, "A12"));
	}
}
