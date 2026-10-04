package com.nikhil.ticket_reservation.controller;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

import com.nikhil.ticket_reservation.auth.AuthenticationService;
import com.nikhil.ticket_reservation.dto.CreateShowRequest;
import com.nikhil.ticket_reservation.dto.CreateShowResponse;
import com.nikhil.ticket_reservation.dto.ReservationResponse;
import com.nikhil.ticket_reservation.dto.ReserveRequest;
import com.nikhil.ticket_reservation.dto.ShowDetailsResponse;
import com.nikhil.ticket_reservation.service.ReservationService;
import com.nikhil.ticket_reservation.service.ShowService;

@RestController
@RequestMapping("/shows")
public class ShowController {

	private final AuthenticationService authenticationService;
	private final ShowService showService;
	private final ReservationService reservationService;

	public ShowController(
			AuthenticationService authenticationService,
			ShowService showService,
			ReservationService reservationService) {
		this.authenticationService = authenticationService;
		this.showService = showService;
		this.reservationService = reservationService;
	}

	@GetMapping("/{id}")
	public ShowDetailsResponse getShow(@PathVariable UUID id) {
		return showService.getShow(id);
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public CreateShowResponse createShow(
			@RequestHeader(name = "Authorization", required = false) String authorization,
			@Valid @RequestBody CreateShowRequest request) {
		authenticationService.requireAdmin(authorization);
		return showService.createShow(request);
	}

	@PostMapping("/{id}/reserve")
	@ResponseStatus(HttpStatus.CREATED)
	public ReservationResponse reserve(
			@PathVariable UUID id,
			@RequestHeader(name = "Authorization", required = false) String authorization,
			@RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
			@Valid @RequestBody ReserveRequest request) {
		String userId = authenticationService.requireUser(authorization);
		return reservationService.reserve(id, userId, idempotencyKey, request);
	}
}
