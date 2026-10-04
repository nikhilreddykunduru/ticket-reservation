package com.nikhil.ticket_reservation.model;

import java.util.UUID;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Table("seats")
public record Seat(
		@Id UUID id,
		@Column("show_id") UUID showId,
		@Column("seat_code") String seatCode,
		String status,
		@Column("reservation_id") UUID reservationId) {
}
