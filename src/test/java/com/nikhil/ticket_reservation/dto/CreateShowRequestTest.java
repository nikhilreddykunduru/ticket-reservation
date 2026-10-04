package com.nikhil.ticket_reservation.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

class CreateShowRequestTest {

	private final JsonMapper mapper = JsonMapper.builder().build();

	@Test
	void deserializesPaiseAsAnInteger() {
		CreateShowRequest request = mapper.readValue("""
				{
				  "name": "friday-night",
				  "seats": ["A1"],
				  "price_paise": 25000,
				  "per_user_limit": 4
				}
				""", CreateShowRequest.class);

		assertEquals(25000L, request.pricePaise());
	}

	@Test
	void rejectsFractionalPaise() {
		assertThrows(JacksonException.class, () -> mapper.readValue("""
				{
				  "name": "friday-night",
				  "seats": ["A1"],
				  "price_paise": 250.00,
				  "per_user_limit": 4
				}
				""", CreateShowRequest.class));
	}
}
