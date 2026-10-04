package com.nikhil.ticket_reservation.dto;

import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;

public record CancellationResponse(
		@JsonProperty("reservation_id") UUID reservationId,
		String status) {
}
