package com.nikhil.ticket_reservation.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
@AutoConfigureMockMvc
class HotSeatConcurrencyTest {

	private static final int USER_COUNT = 100;

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	private UUID showId;

	@BeforeEach
	void createShowWithSingleSeat() {
		showId = UUID.randomUUID();
		jdbcTemplate.update(
				"INSERT INTO shows (id, name, price_paise, per_user_limit) VALUES (?, ?, ?, ?)",
				showId, "hot-seat-" + showId, 25000L, 1);
		jdbcTemplate.update(
				"INSERT INTO seats (id, show_id, seat_code, status) VALUES (?, ?, ?, 'AVAILABLE')",
				UUID.randomUUID(), showId, "A12");
	}

	@AfterEach
	void removeShow() {
		jdbcTemplate.update("DELETE FROM idempotency_keys WHERE show_id = ?", showId);
		jdbcTemplate.update("DELETE FROM reservation_seats WHERE reservation_id IN "
				+ "(SELECT id FROM reservations WHERE show_id = ?)", showId);
		jdbcTemplate.update("DELETE FROM seats WHERE show_id = ?", showId);
		jdbcTemplate.update("DELETE FROM reservations WHERE show_id = ?", showId);
		jdbcTemplate.update("DELETE FROM user_show_counters WHERE show_id = ?", showId);
		jdbcTemplate.update("DELETE FROM shows WHERE id = ?", showId);
	}

	@Test
	void onlyOneConcurrentUserCanReserveTheHotSeat() throws Exception {
		ExecutorService executor = Executors.newFixedThreadPool(USER_COUNT);
		CountDownLatch ready = new CountDownLatch(USER_COUNT);
		CountDownLatch start = new CountDownLatch(1);
		List<Future<MvcResult>> responses = new ArrayList<>(USER_COUNT);

		try {
			for (int user = 0; user < USER_COUNT; user++) {
				int userNumber = user;
				responses.add(executor.submit(() -> {
					ready.countDown();
					if (!start.await(30, TimeUnit.SECONDS)) {
						throw new IllegalStateException("Timed out waiting for concurrent request start");
					}
					MvcResult result = mockMvc.perform(post("/shows/{id}/reserve", showId)
							.header("Authorization", "Bearer user:concurrent-" + userNumber)
							.header("Idempotency-Key", "hot-seat-" + userNumber)
							.contentType(MediaType.APPLICATION_JSON)
							.content("{\"seats\":[\"A12\"]}"))
							.andReturn();
					return result;
				}));
			}

			assertTrue(ready.await(30, TimeUnit.SECONDS), "All users should reach the start barrier");
			start.countDown();

			int created = 0;
			int conflicts = 0;
			for (Future<MvcResult> response : responses) {
				MvcResult result = response.get(60, TimeUnit.SECONDS);
				int status = result.getResponse().getStatus();
				if (status == 201) {
					created++;
				} else if (status == 409) {
					conflicts++;
					assertTrue(result.getResponse().getContentAsString().contains("\"error\":\"seat_taken\""),
							"Hot-seat contention should return the seat_taken domain error");
				} else {
					throw new AssertionError("Unexpected response status: " + status);
				}
			}

			assertEquals(1, created, "Exactly one user should reserve A12");
			assertEquals(99, conflicts, "All other users should receive a conflict");
			assertEquals(1, jdbcTemplate.queryForObject(
					"SELECT COUNT(*) FROM reservations WHERE show_id = ?", Integer.class, showId));
			assertEquals("CONFIRMED", jdbcTemplate.queryForObject(
					"SELECT status FROM seats WHERE show_id = ? AND seat_code = 'A12'", String.class, showId));
		} finally {
			start.countDown();
			executor.shutdownNow();
			assertTrue(executor.awaitTermination(30, TimeUnit.SECONDS), "Request workers should stop");
		}
	}
}
