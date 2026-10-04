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
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class UserLimitConcurrencyTest {

	private static final int REQUEST_COUNT = 10;
	private static final int PER_USER_LIMIT = 4;
	private static final String USER_ID = "concurrent-user";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	private UUID showId;

	@BeforeEach
	void createShowWithEnoughSeats() {
		showId = UUID.randomUUID();
		jdbcTemplate.update(
				"INSERT INTO shows (id, name, price_paise, per_user_limit) VALUES (?, ?, ?, ?)",
				showId, "user-limit-concurrency-" + showId, 25000L, PER_USER_LIMIT);
		for (int seat = 0; seat < REQUEST_COUNT; seat++) {
			jdbcTemplate.update(
					"INSERT INTO seats (id, show_id, seat_code, status) VALUES (?, ?, ?, 'AVAILABLE')",
					UUID.randomUUID(), showId, "A" + seat);
		}
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
	void concurrentReservationsForOneUserDoNotExceedPerUserLimit() throws Exception {
		ExecutorService executor = Executors.newFixedThreadPool(REQUEST_COUNT);
		CountDownLatch ready = new CountDownLatch(REQUEST_COUNT);
		CountDownLatch start = new CountDownLatch(1);
		List<Future<Integer>> responses = new ArrayList<>(REQUEST_COUNT);

		try {
			for (int seat = 0; seat < REQUEST_COUNT; seat++) {
				int seatNumber = seat;
				responses.add(executor.submit(() -> {
					ready.countDown();
					if (!start.await(30, TimeUnit.SECONDS)) {
						throw new IllegalStateException("Timed out waiting for concurrent request start");
					}
					MvcResult result = mockMvc.perform(post("/shows/{id}/reserve", showId)
							.header("Authorization", "Bearer user:" + USER_ID)
							.header("Idempotency-Key", "user-limit-" + seatNumber)
							.contentType(MediaType.APPLICATION_JSON)
							.content("{\"seats\":[\"A" + seatNumber + "\"]}"))
							.andReturn();
					return result.getResponse().getStatus();
				}));
			}

			assertTrue(ready.await(30, TimeUnit.SECONDS), "All requests should reach the start barrier");
			start.countDown();

			int created = 0;
			int conflicts = 0;
			for (Future<Integer> response : responses) {
				int status = response.get(60, TimeUnit.SECONDS);
				if (status == 201) {
					created++;
				} else if (status == 409) {
					conflicts++;
				} else {
					throw new AssertionError("Unexpected response status: " + status);
				}
			}

			int confirmedSeats = jdbcTemplate.queryForObject(
					"SELECT COUNT(*) FROM seats WHERE show_id = ? AND status = 'CONFIRMED'",
					Integer.class, showId);
			assertEquals(PER_USER_LIMIT, created);
			assertEquals(REQUEST_COUNT - PER_USER_LIMIT, conflicts);
			assertTrue(confirmedSeats <= PER_USER_LIMIT);
			assertEquals(PER_USER_LIMIT, confirmedSeats);
			assertEquals(PER_USER_LIMIT, jdbcTemplate.queryForObject(
					"SELECT occupied_count FROM user_show_counters WHERE show_id = ? AND user_id = ?",
					Integer.class, showId, USER_ID));
		} finally {
			start.countDown();
			executor.shutdownNow();
			assertTrue(executor.awaitTermination(30, TimeUnit.SECONDS), "Request workers should stop");
		}
	}
}
