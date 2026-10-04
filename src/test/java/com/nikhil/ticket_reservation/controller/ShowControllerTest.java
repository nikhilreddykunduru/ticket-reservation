package com.nikhil.ticket_reservation.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
}
