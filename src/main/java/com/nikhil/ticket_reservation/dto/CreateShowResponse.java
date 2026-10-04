package com.nikhil.ticket_reservation.dto;

import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;

public record CreateShowResponse(
		UUID id,
		String name,
		List<String> seats,
		@JsonProperty("price_paise") long pricePaise,
		@JsonProperty("per_user_limit") int perUserLimit) {
}
