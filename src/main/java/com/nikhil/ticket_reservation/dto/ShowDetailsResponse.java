package com.nikhil.ticket_reservation.dto;

import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ShowDetailsResponse(
		UUID id,
		String name,
		@JsonProperty("price_paise") long pricePaise,
		@JsonProperty("total_seats") int totalSeats,
		@JsonProperty("available_seats") int availableSeats,
		@JsonProperty("held_seats") int heldSeats,
		@JsonProperty("confirmed_seats") int confirmedSeats,
		List<SeatDetails> seats) {

	public record SeatDetails(String seat, String status) {
	}
}
