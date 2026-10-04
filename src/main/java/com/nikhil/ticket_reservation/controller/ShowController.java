package com.nikhil.ticket_reservation.controller;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.nikhil.ticket_reservation.auth.AuthenticationService;
import com.nikhil.ticket_reservation.dto.CreateShowRequest;
import com.nikhil.ticket_reservation.dto.CreateShowResponse;
import com.nikhil.ticket_reservation.service.ShowService;

@RestController
@RequestMapping("/shows")
public class ShowController {

	private final AuthenticationService authenticationService;
	private final ShowService showService;

	public ShowController(AuthenticationService authenticationService, ShowService showService) {
		this.authenticationService = authenticationService;
		this.showService = showService;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public CreateShowResponse createShow(
			@RequestHeader(name = "Authorization", required = false) String authorization,
			@Valid @RequestBody CreateShowRequest request) {
		authenticationService.requireAdmin(authorization);
		return showService.createShow(request);
	}
}
