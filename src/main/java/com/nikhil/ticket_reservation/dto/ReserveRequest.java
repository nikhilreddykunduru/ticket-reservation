package com.nikhil.ticket_reservation.dto;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

public record ReserveRequest(@NotEmpty List<@NotBlank String> seats) {
}
