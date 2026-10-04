package com.nikhil.ticket_reservation.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ShowControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Value("${ticket-reservation.admin-token}")
	private String adminToken;

	@Test
	void createsShowAndAvailableSeatsForAdmin() throws Exception {
		String showName = "friday-night-" + UUID.randomUUID();
		String request = """
				{
				  "name": "%s",
				  "seats": ["A1", "A2", "A3"],
				  "price_paise": 25000,
				  "per_user_limit": 4
				}
				""".formatted(showName);

		mockMvc.perform(post("/shows")
				.header("Authorization", "Bearer " + adminToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content(request))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.name").value(showName))
				.andExpect(jsonPath("$.price_paise").value(25000))
				.andExpect(jsonPath("$.per_user_limit").value(4));

		List<String> seatStatuses = jdbcTemplate.queryForList(
				"SELECT status FROM seats WHERE show_id = (SELECT id FROM shows WHERE name = ?) ORDER BY seat_code",
				String.class,
				showName);
		assertEquals(List.of("AVAILABLE", "AVAILABLE", "AVAILABLE"), seatStatuses);
	}

	@Test
	void requiresAdminBearerToken() throws Exception {
		mockMvc.perform(post("/shows")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "name": "friday-night",
						  "seats": ["A1"],
						  "price_paise": 25000,
						  "per_user_limit": 4
						}
						"""))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void getsShowDetailsAndSeatAvailability() throws Exception {
		UUID showId = UUID.randomUUID();
		jdbcTemplate.update(
				"INSERT INTO shows (id, name, price_paise, per_user_limit) VALUES (?, ?, ?, ?)",
				showId, "friday-night", 25000L, 4);
		for (String seatCode : List.of("A1", "A2", "A3")) {
			jdbcTemplate.update(
					"INSERT INTO seats (id, show_id, seat_code, status) VALUES (?, ?, ?, 'AVAILABLE')",
					UUID.randomUUID(), showId, seatCode);
		}

		mockMvc.perform(get("/shows/{id}", showId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(showId.toString()))
				.andExpect(jsonPath("$.name").value("friday-night"))
				.andExpect(jsonPath("$.price_paise").value(25000))
				.andExpect(jsonPath("$.total_seats").value(3))
				.andExpect(jsonPath("$.available_seats").value(3))
				.andExpect(jsonPath("$.held_seats").value(0))
				.andExpect(jsonPath("$.confirmed_seats").value(0))
				.andExpect(jsonPath("$.seats[0].seat").value("A1"))
				.andExpect(jsonPath("$.seats[0].status").value("available"))
				.andExpect(jsonPath("$.seats[1].seat").value("A2"))
				.andExpect(jsonPath("$.seats[1].status").value("available"))
				.andExpect(jsonPath("$.seats[2].seat").value("A3"))
				.andExpect(jsonPath("$.seats[2].status").value("available"));
	}

	@Test
	void returnsNotFoundForUnknownShow() throws Exception {
		mockMvc.perform(get("/shows/{id}", UUID.randomUUID()))
				.andExpect(status().isNotFound());
	}

	@Test
	void countsConfirmedSeats() throws Exception {
		UUID showId = UUID.randomUUID();
		jdbcTemplate.update(
				"INSERT INTO shows (id, name, price_paise, per_user_limit) VALUES (?, ?, ?, ?)",
				showId, "saturday-night", 30000L, 2);
		jdbcTemplate.update(
				"INSERT INTO seats (id, show_id, seat_code, status) VALUES (?, ?, ?, 'AVAILABLE')",
				UUID.randomUUID(), showId, "A1");
		jdbcTemplate.update(
				"INSERT INTO seats (id, show_id, seat_code, status) VALUES (?, ?, ?, 'CONFIRMED')",
				UUID.randomUUID(), showId, "A2");

		mockMvc.perform(get("/shows/{id}", showId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.total_seats").value(2))
				.andExpect(jsonPath("$.available_seats").value(1))
				.andExpect(jsonPath("$.held_seats").value(0))
				.andExpect(jsonPath("$.confirmed_seats").value(1))
				.andExpect(jsonPath("$.seats[1].seat").value("A2"))
				.andExpect(jsonPath("$.seats[1].status").value("confirmed"));
	}

	@Test
	void reservesSeatsAtomicallyAndReturnsExpectedResponse() throws Exception {
		UUID showId = insertShow("reserve-happy-path", 25000L, 4, "A1", "A2");

		mockMvc.perform(post("/shows/{id}/reserve", showId)
				.header("Authorization", "Bearer user:alice")
				.header("Idempotency-Key", "key-1")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"seats\":[\"A1\"]}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.reservation_id").isNotEmpty())
				.andExpect(jsonPath("$.show_id").value(showId.toString()))
				.andExpect(jsonPath("$.user_id").value("alice"))
				.andExpect(jsonPath("$.seats[0]").value("A1"))
				.andExpect(jsonPath("$.amount_paise").value(25000))
				.andExpect(jsonPath("$.status").value("confirmed"));

		assertEquals("CONFIRMED", jdbcTemplate.queryForObject(
				"SELECT status FROM seats WHERE show_id = ? AND seat_code = 'A1'", String.class, showId));
		assertEquals(1, jdbcTemplate.queryForObject(
				"SELECT occupied_count FROM user_show_counters WHERE show_id = ? AND user_id = 'alice'",
				Integer.class, showId));
	}

	@Test
	void idempotentRetryReturnsOriginalReservationAndChangedBodyConflicts() throws Exception {
		UUID showId = insertShow("reserve-idempotency", 25000L, 4, "A1", "A2");
		String firstResponse = mockMvc.perform(post("/shows/{id}/reserve", showId)
				.header("Authorization", "Bearer user:alice")
				.header("Idempotency-Key", "same-key")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"seats\":[\"A1\"]}"))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();

		String replayResponse = mockMvc.perform(post("/shows/{id}/reserve", showId)
				.header("Authorization", "Bearer user:alice")
				.header("Idempotency-Key", "same-key")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"seats\":[\"A1\"]}"))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		assertEquals(firstResponse, replayResponse);

		mockMvc.perform(post("/shows/{id}/reserve", showId)
				.header("Authorization", "Bearer user:alice")
				.header("Idempotency-Key", "same-key")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"seats\":[\"A2\"]}"))
				.andExpect(status().isConflict());

		assertEquals(1, jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM reservations WHERE show_id = ?", Integer.class, showId));
	}

	@Test
	void multiSeatReservationConflictsWithoutBookingAnyAvailableSeat() throws Exception {
		UUID showId = insertShow("reserve-all-or-nothing", 25000L, 4, "A1", "A2");
		jdbcTemplate.update(
				"UPDATE seats SET status = 'CONFIRMED' WHERE show_id = ? AND seat_code = 'A2'",
				showId);

		mockMvc.perform(post("/shows/{id}/reserve", showId)
				.header("Authorization", "Bearer user:alice")
				.header("Idempotency-Key", "two-seats")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"seats\":[\"A1\",\"A2\"]}"))
				.andExpect(status().isConflict());

		assertEquals("AVAILABLE", jdbcTemplate.queryForObject(
				"SELECT status FROM seats WHERE show_id = ? AND seat_code = 'A1'", String.class, showId));
		assertEquals(0, jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM reservations WHERE show_id = ?", Integer.class, showId));
	}

	@Test
	void enforcesPerUserSeatLimit() throws Exception {
		UUID showId = insertShow("reserve-limit", 25000L, 1, "A1", "A2");

		mockMvc.perform(post("/shows/{id}/reserve", showId)
				.header("Authorization", "Bearer user:alice")
				.header("Idempotency-Key", "first-seat")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"seats\":[\"A1\"]}"))
				.andExpect(status().isCreated());

		mockMvc.perform(post("/shows/{id}/reserve", showId)
				.header("Authorization", "Bearer user:alice")
				.header("Idempotency-Key", "second-seat")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"seats\":[\"A2\",\"missing\"]}"))
				.andExpect(status().isConflict())
				.andExpect(content().json("{\"error\":\"per_user_limit\"}", true));

		assertEquals(1, jdbcTemplate.queryForObject(
				"SELECT occupied_count FROM user_show_counters WHERE show_id = ? AND user_id = 'alice'",
				Integer.class, showId));
		assertEquals("AVAILABLE", jdbcTemplate.queryForObject(
				"SELECT status FROM seats WHERE show_id = ? AND seat_code = 'A2'", String.class, showId));
	}

	private UUID insertShow(String name, long pricePaise, int perUserLimit, String... seats) {
		UUID showId = UUID.randomUUID();
		jdbcTemplate.update(
				"INSERT INTO shows (id, name, price_paise, per_user_limit) VALUES (?, ?, ?, ?)",
				showId, name, pricePaise, perUserLimit);
		for (String seatCode : seats) {
			jdbcTemplate.update(
					"INSERT INTO seats (id, show_id, seat_code, status) VALUES (?, ?, ?, 'AVAILABLE')",
					UUID.randomUUID(), showId, seatCode);
		}
		return showId;
	}
}
