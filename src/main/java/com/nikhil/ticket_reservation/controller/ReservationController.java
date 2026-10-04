package com.nikhil.ticket_reservation.controller;

import java.util.UUID;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.nikhil.ticket_reservation.auth.AuthenticationService;
import com.nikhil.ticket_reservation.dto.CancellationResponse;
import com.nikhil.ticket_reservation.service.ReservationService;

@RestController
@RequestMapping("/reservations")
public class ReservationController {

	private final AuthenticationService authenticationService;
	private final ReservationService reservationService;

	public ReservationController(
			AuthenticationService authenticationService,
			ReservationService reservationService) {
		this.authenticationService = authenticationService;
		this.reservationService = reservationService;
	}

	@PostMapping("/{id}/cancel")
	public CancellationResponse cancel(
			@PathVariable UUID id,
			@RequestHeader(name = "Authorization", required = false) String authorization) {
		UUID reservationId = reservationService.cancel(id, authenticationService.requireUser(authorization));
		return new CancellationResponse(reservationId, "cancelled");
	}
}
