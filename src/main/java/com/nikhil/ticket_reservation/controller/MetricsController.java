package com.nikhil.ticket_reservation.controller;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.nikhil.ticket_reservation.service.MetricsService;

@RestController
public class MetricsController {

	private static final String PROMETHEUS_CONTENT_TYPE = "text/plain; version=0.0.4; charset=utf-8";

	private final MetricsService metricsService;

	public MetricsController(MetricsService metricsService) {
		this.metricsService = metricsService;
	}

	@GetMapping(value = "/metrics", produces = MediaType.TEXT_PLAIN_VALUE)
	public ResponseEntity<String> metrics() {
		return ResponseEntity.ok()
				.header("Content-Type", PROMETHEUS_CONTENT_TYPE)
				.body(metricsService.scrape());
	}
}
