package com.nikhil.ticket_reservation.logging;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class RequestLogFilter extends OncePerRequestFilter {

	private static final Logger logger = LoggerFactory.getLogger(RequestLogFilter.class);
	private static final String REQUEST_ID_HEADER = "X-Request-Id";
	private static final Pattern RESERVATION_PATH = Pattern.compile("^/shows/([^/]+)/reserve/?$");
	private static final String USER_TOKEN_PREFIX = "Bearer user:";

	@Override
	protected void doFilterInternal(
			HttpServletRequest request,
			HttpServletResponse response,
			FilterChain filterChain) throws ServletException, IOException {
		String requestId = UUID.randomUUID().toString();
		response.setHeader(REQUEST_ID_HEADER, requestId);
		long startedAt = System.nanoTime();
		int status = response.getStatus();

		try {
			filterChain.doFilter(request, response);
			status = response.getStatus();
		} catch (IOException | ServletException | RuntimeException | Error exception) {
			status = response.getStatus() == HttpServletResponse.SC_OK
					? HttpServletResponse.SC_INTERNAL_SERVER_ERROR
					: response.getStatus();
			throw exception;
		} finally {
			logRequest(request, requestId, status, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt));
		}
	}

	private void logRequest(HttpServletRequest request, String requestId, int status, long durationMs) {
		String path = request.getRequestURI().substring(request.getContextPath().length());
		var log = logger.atInfo()
				.addKeyValue("request_id", requestId)
				.addKeyValue("method", request.getMethod())
				.addKeyValue("path", path)
				.addKeyValue("status", status)
				.addKeyValue("duration_ms", durationMs);

		Matcher reservationPath = RESERVATION_PATH.matcher(path);
		if (reservationPath.matches()) {
			log.addKeyValue("user_id", extractUserId(request.getHeader("Authorization")))
					.addKeyValue("show_id", reservationPath.group(1));
		}

		log.log("HTTP request");
	}

	private String extractUserId(String authorization) {
		if (authorization == null || !authorization.startsWith(USER_TOKEN_PREFIX)) {
			return null;
		}
		String userId = authorization.substring(USER_TOKEN_PREFIX.length());
		return userId.isBlank() ? null : userId;
	}
}
