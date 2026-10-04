package com.nikhil.ticket_reservation.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.nikhil.ticket_reservation.dto.ReservationResponse;
import com.nikhil.ticket_reservation.dto.ReserveRequest;
import com.nikhil.ticket_reservation.model.Seat;
import com.nikhil.ticket_reservation.model.Show;
import com.nikhil.ticket_reservation.repository.IdempotencyRepository;
import com.nikhil.ticket_reservation.repository.ReservationRepository;
import com.nikhil.ticket_reservation.repository.ShowRepository;

@Service
public class ReservationService {

	private final ReservationRepository reservationRepository;
	private final IdempotencyRepository idempotencyRepository;
	private final ShowRepository showRepository;

	public ReservationService(
			ReservationRepository reservationRepository,
			IdempotencyRepository idempotencyRepository,
			ShowRepository showRepository) {
		this.reservationRepository = reservationRepository;
		this.idempotencyRepository = idempotencyRepository;
		this.showRepository = showRepository;
	}

	@Transactional
	public ReservationResponse reserve(UUID showId, String userId, String idempotencyKey, ReserveRequest request) {
		if (idempotencyKey == null || idempotencyKey.isBlank()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Idempotency-Key header is required");
		}
		if (request.seats().stream().distinct().count() != request.seats().size()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Seat names must be unique");
		}

		List<String> requestedSeats = request.seats().stream().sorted().toList();
		String requestHash = hashRequest(requestedSeats);
		var existingHash = idempotencyRepository.findRequestHash(showId, userId, idempotencyKey);
		if (existingHash.isPresent()) {
			return replayOrReject(showId, userId, idempotencyKey, requestHash, existingHash.get());
		}

		Show show = showRepository.findById(showId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Show not found"));

		int occupiedCount = reservationRepository.createAndLockUserShowCounter(showId, userId);

		// Another request using the same key may have completed while this request waited for the counter lock.
		existingHash = idempotencyRepository.findRequestHash(showId, userId, idempotencyKey);
		if (existingHash.isPresent()) {
			return replayOrReject(showId, userId, idempotencyKey, requestHash, existingHash.get());
		}

		if ((long) occupiedCount + requestedSeats.size() > show.perUserLimit()) {
			throw new PerUserLimitExceededException();
		}

		List<Seat> seats = reservationRepository.lockSeatsByCode(showId, requestedSeats);
		if (seats.size() != requestedSeats.size()
				|| seats.stream().anyMatch(seat -> !seat.status().equals("AVAILABLE"))) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "One or more requested seats are unavailable");
		}

		long amountPaise;
		try {
			amountPaise = Math.multiplyExact(show.pricePaise(), seats.size());
		} catch (ArithmeticException exception) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Reservation amount is too large");
		}

		UUID reservationId = UUID.randomUUID();
		reservationRepository.createReservation(reservationId, showId, userId, amountPaise);
		reservationRepository.createReservationSeats(reservationId, seats);
		reservationRepository.confirmSeats(reservationId, seats);
		reservationRepository.incrementOccupiedCount(showId, userId, seats.size());
		idempotencyRepository.createRecord(
				UUID.randomUUID(), showId, userId, idempotencyKey, requestHash, reservationId);

		return new ReservationResponse(reservationId, showId, userId, requestedSeats, amountPaise, "confirmed");
	}

	@Transactional
	public UUID cancel(UUID reservationId, String userId) {
		ReservationRepository.Reservation reservation = reservationRepository.lockReservation(reservationId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Reservation not found"));

		if (!reservation.userId().equals(userId)) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the reservation owner can cancel it");
		}
		if (!reservation.status().equals("CONFIRMED")) {
			throw new ReservationNotCancellableException();
		}

		// Reservation requests lock the user's counter before seats; keep that order to avoid deadlocks.
		reservationRepository.lockUserShowCounter(reservation.showId(), userId);
		List<Seat> seats = reservationRepository.lockSeatsByReservation(reservationId);
		if (seats.isEmpty() || seats.stream().anyMatch(seat ->
				!seat.status().equals("CONFIRMED") || !reservationId.equals(seat.reservationId()))) {
			throw new ReservationNotCancellableException();
		}

		if (reservationRepository.cancelReservation(reservationId) != 1
				|| reservationRepository.releaseReservationSeats(reservationId, seats) != seats.size()
				|| reservationRepository.decrementOccupiedCount(
						reservation.showId(), userId, seats.size()) != 1) {
			throw new ReservationNotCancellableException();
		}

		return reservationId;
	}

	private ReservationResponse replayOrReject(
			UUID showId, String userId, String key, String requestHash, String existingHash) {
		if (!existingHash.equals(requestHash)) {
			throw new IdempotencyConflictException();
		}
		return idempotencyRepository.findReservation(showId, userId, key)
				.orElseThrow(() -> new IllegalStateException("Idempotency record has no reservation"));
	}

	private String hashRequest(List<String> seats) {
		String canonicalRequest = seats.stream()
				.map(seat -> seat.length() + ":" + seat)
				.collect(java.util.stream.Collectors.joining());
		try {
			return HexFormat.of().formatHex(
					MessageDigest.getInstance("SHA-256").digest(canonicalRequest.getBytes(StandardCharsets.UTF_8)));
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 is not available", exception);
		}
	}
}
