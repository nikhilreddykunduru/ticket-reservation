package com.nikhil.ticket_reservation.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
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
class CancellationIntegrationTest extends PostgresIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void onlyOwnerCanCancelAndReleasedSeatCanBeReservedAgain() throws Exception {
		UUID showId = insertShowWithSeat();
		assertShowReconciles(showId, 1, 0, 0);
		UUID reservationId = reserve(showId, "alice", "alice-first");
		assertShowReconciles(showId, 0, 0, 1);

		mockMvc.perform(post("/reservations/{id}/cancel", reservationId)
				.header("Authorization", "Bearer user:bob"))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error").value("unauthorized_action"));

		assertEquals("CONFIRMED", reservationStatus(reservationId));
		assertEquals("CONFIRMED", seatStatus(showId));
		assertEquals(1, occupiedCount(showId, "alice"));
		assertShowReconciles(showId, 0, 0, 1);

		mockMvc.perform(post("/reservations/{id}/cancel", reservationId)
				.header("Authorization", "Bearer user:alice"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.reservation_id").value(reservationId.toString()))
				.andExpect(jsonPath("$.status").value("cancelled"));

		assertEquals("CANCELLED", reservationStatus(reservationId));
		assertEquals("AVAILABLE", seatStatus(showId));
		assertNull(seatReservationId(showId));
		assertEquals(0, occupiedCount(showId, "alice"));
		assertShowReconciles(showId, 1, 0, 0);

		mockMvc.perform(post("/reservations/{id}/cancel", reservationId)
				.header("Authorization", "Bearer user:alice"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error").value("already_cancelled"));
		assertEquals("AVAILABLE", seatStatus(showId));
		assertShowReconciles(showId, 1, 0, 0);

		reserve(showId, "bob", "bob-after-cancel");
		assertEquals("CONFIRMED", seatStatus(showId));
		assertEquals(1, occupiedCount(showId, "bob"));
		assertEquals(0, occupiedCount(showId, "alice"));
		assertShowReconciles(showId, 0, 0, 1);
	}

	@Test
	void returnsNotFoundForUnknownReservation() throws Exception {
		mockMvc.perform(post("/reservations/{id}/cancel", UUID.randomUUID())
				.header("Authorization", "Bearer user:alice"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error").value("resource_not_found"));
	}

	@Test
	void cancelsEverySeatAndDecrementsTheCounterByTheReservationSize() throws Exception {
		UUID showId = UUID.randomUUID();
		jdbcTemplate.update(
				"INSERT INTO shows (id, name, price_paise, per_user_limit) VALUES (?, ?, ?, ?)",
				showId, "cancel-multiple-" + showId, 25000L, 4);
		for (String seatCode : new String[] { "A1", "A2" }) {
			jdbcTemplate.update(
					"INSERT INTO seats (id, show_id, seat_code, status) VALUES (?, ?, ?, 'AVAILABLE')",
					UUID.randomUUID(), showId, seatCode);
		}
		mockMvc.perform(post("/shows/{id}/reserve", showId)
				.header("Authorization", "Bearer user:alice")
				.header("Idempotency-Key", "alice-multiple")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"seats\":[\"A1\",\"A2\"]}"))
				.andExpect(status().isCreated());
		UUID reservationId = jdbcTemplate.queryForObject(
				"SELECT id FROM reservations WHERE show_id = ? AND user_id = 'alice'",
				UUID.class, showId);

		mockMvc.perform(post("/reservations/{id}/cancel", reservationId)
				.header("Authorization", "Bearer user:alice"))
				.andExpect(status().isOk());

		assertShowReconciles(showId, 2, 0, 0);
		assertEquals(2, jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM seats WHERE show_id = ? AND status = 'AVAILABLE'",
				Integer.class, showId));
		assertEquals(0, occupiedCount(showId, "alice"));
	}

	private UUID insertShowWithSeat() {
		UUID showId = UUID.randomUUID();
		jdbcTemplate.update(
				"INSERT INTO shows (id, name, price_paise, per_user_limit) VALUES (?, ?, ?, ?)",
				showId, "cancel-" + showId, 25000L, 4);
		jdbcTemplate.update(
				"INSERT INTO seats (id, show_id, seat_code, status) VALUES (?, ?, ?, 'AVAILABLE')",
				UUID.randomUUID(), showId, "A1");
		return showId;
	}

	private UUID reserve(UUID showId, String userId, String idempotencyKey) throws Exception {
		mockMvc.perform(post("/shows/{id}/reserve", showId)
				.header("Authorization", "Bearer user:" + userId)
				.header("Idempotency-Key", idempotencyKey)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"seats\":[\"A1\"]}"))
				.andExpect(status().isCreated());
		return jdbcTemplate.queryForObject(
				"SELECT id FROM reservations WHERE show_id = ? AND user_id = ?",
				UUID.class, showId, userId);
	}

	private String reservationStatus(UUID reservationId) {
		return jdbcTemplate.queryForObject(
				"SELECT status FROM reservations WHERE id = ?", String.class, reservationId);
	}

	private String seatStatus(UUID showId) {
		return jdbcTemplate.queryForObject(
				"SELECT status FROM seats WHERE show_id = ?", String.class, showId);
	}

	private UUID seatReservationId(UUID showId) {
		return jdbcTemplate.queryForObject(
				"SELECT reservation_id FROM seats WHERE show_id = ?", UUID.class, showId);
	}

	private int occupiedCount(UUID showId, String userId) {
		return jdbcTemplate.queryForObject(
				"SELECT occupied_count FROM user_show_counters WHERE show_id = ? AND user_id = ?",
				Integer.class, showId, userId);
	}

	private void assertShowReconciles(UUID showId, int expectedAvailable, int expectedHeld, int expectedConfirmed)
			throws Exception {
		int total = expectedAvailable + expectedHeld + expectedConfirmed;
		mockMvc.perform(get("/shows/{id}", showId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.available_seats").value(expectedAvailable))
				.andExpect(jsonPath("$.held_seats").value(expectedHeld))
				.andExpect(jsonPath("$.confirmed_seats").value(expectedConfirmed))
				.andExpect(jsonPath("$.total_seats").value(total))
				.andExpect(jsonPath("$.seats.length()").value(total));
	}
}
