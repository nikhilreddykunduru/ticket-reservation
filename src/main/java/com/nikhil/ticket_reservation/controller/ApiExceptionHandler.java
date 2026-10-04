package com.nikhil.ticket_reservation.controller;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.nikhil.ticket_reservation.service.PerUserLimitExceededException;

@RestControllerAdvice
public class ApiExceptionHandler {

	@ExceptionHandler(PerUserLimitExceededException.class)
	public ResponseEntity<Map<String, String>> handlePerUserLimitExceeded() {
		return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", "per_user_limit"));
	}
}
