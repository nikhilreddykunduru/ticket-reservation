package com.nikhil.ticket_reservation.controller;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {

	@GetMapping("/health/live")
	public Map<String, String> live() {
		return Map.of("status", "ok");
	}
}
