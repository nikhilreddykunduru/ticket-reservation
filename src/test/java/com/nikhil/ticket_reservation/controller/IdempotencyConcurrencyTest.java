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

import io.micrometer.core.instrument.MeterRegistry;
import com.nikhil.ticket_reservation.PostgresIntegrationTest;

@SpringBootTest
@AutoConfigureMockMvc
class IdempotencyConcurrencyTest extends PostgresIntegrationTest {

	private static final int REQUEST_COUNT = 100;
	private static final String USER_ID = "idempotency-user";
	private static final String IDEMPOTENCY_KEY = "same-key";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private MeterRegistry meterRegistry;

	private UUID showId;

	@BeforeEach
	void createShowWithOneAvailableSeat() {
		showId = UUID.randomUUID();
		jdbcTemplate.update(
				"INSERT INTO shows (id, name, price_paise, per_user_limit) VALUES (?, ?, ?, ?)",
				showId, "idempotency-" + showId, 25000L, 1);
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
	void replaysSameRequestRejectsChangedRequestAndHandlesOneHundredConcurrentRequests() throws Exception {
		double replayCountBefore = meterRegistry.counter("reservations_replayed_total").count();
		ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
		CountDownLatch ready = new CountDownLatch(REQUEST_COUNT);
		CountDownLatch start = new CountDownLatch(1);
		List<Future<MvcResult>> responses = new ArrayList<>(REQUEST_COUNT);

		try {
			for (int request = 0; request < REQUEST_COUNT; request++) {
				responses.add(executor.submit(() -> {
					ready.countDown();
					if (!start.await(30, TimeUnit.SECONDS)) {
						throw new IllegalStateException("Timed out waiting for concurrent request start");
					}
					return reserve("[\"A12\"]");
				}));
			}

			assertTrue(ready.await(30, TimeUnit.SECONDS), "All requests should reach the start barrier");
			start.countDown();

			String originalResponse = null;
			int replays = 0;
			for (Future<MvcResult> response : responses) {
				MvcResult result = response.get(60, TimeUnit.SECONDS);
				assertEquals(201, result.getResponse().getStatus());
				String body = result.getResponse().getContentAsString();
				if (originalResponse == null) {
					originalResponse = body;
				} else {
					assertEquals(originalResponse, body);
					replays++;
				}
			}
			assertEquals(REQUEST_COUNT - 1, replays);
			assertEquals(REQUEST_COUNT - 1, meterRegistry.counter("reservations_replayed_total").count()
					- replayCountBefore);

			MvcResult retry = reserve("[\"A12\"]");
			assertEquals(201, retry.getResponse().getStatus());
			assertEquals(originalResponse, retry.getResponse().getContentAsString());

			MvcResult changedRequest = reserve("[\"A13\"]");
			assertEquals(409, changedRequest.getResponse().getStatus());
			assertTrue(changedRequest.getResponse().getContentAsString().contains("idempotency_conflict"));

			assertEquals(1, jdbcTemplate.queryForObject(
					"SELECT COUNT(*) FROM reservations WHERE show_id = ?", Integer.class, showId));
			assertEquals(1, jdbcTemplate.queryForObject(
					"SELECT COUNT(*) FROM idempotency_keys WHERE show_id = ? AND user_id = ? "
							+ "AND idempotency_key = ?",
					Integer.class, showId, USER_ID, IDEMPOTENCY_KEY));
		} finally {
			start.countDown();
			executor.shutdownNow();
			assertTrue(executor.awaitTermination(30, TimeUnit.SECONDS), "Request workers should stop");
		}
	}

	private MvcResult reserve(String seats) throws Exception {
		return mockMvc.perform(post("/shows/{id}/reserve", showId)
				.header("Authorization", "Bearer user:" + USER_ID)
				.header("Idempotency-Key", IDEMPOTENCY_KEY)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"seats\":" + seats + "}"))
				.andReturn();
	}
}
