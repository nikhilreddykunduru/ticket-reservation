package com.nikhil.ticket_reservation.logging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.stream.Collectors;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RequestLogFilterTest {

	private final RequestLogFilter filter = new RequestLogFilter();

	@Test
	void logsReservationDetailsWithoutAuthorizationToken() throws Exception {
		ListAppender<ILoggingEvent> appender = attachAppender();
		MockHttpServletRequest request = new MockHttpServletRequest("POST", "/shows/show-123/reserve");
		request.addHeader("Authorization", "Bearer user:alice");
		MockHttpServletResponse response = new MockHttpServletResponse();
		FilterChain chain = (servletRequest, servletResponse) ->
				((MockHttpServletResponse) servletResponse).setStatus(409);

		try {
			filter.doFilter(request, response, chain);

			assertEquals(409, response.getStatus());
			assertNotNull(response.getHeader("X-Request-Id"));
			Map<String, Object> fields = fieldsFrom(appender.list.getFirst());
			assertEquals(response.getHeader("X-Request-Id"), fields.get("request_id"));
			assertEquals("POST", fields.get("method"));
			assertEquals("/shows/show-123/reserve", fields.get("path"));
			assertEquals(409, fields.get("status"));
			assertTrue(fields.get("duration_ms") instanceof Number);
			assertEquals("alice", fields.get("user_id"));
			assertEquals("show-123", fields.get("show_id"));
			assertFalse(fields.containsKey("Authorization"));
			assertFalse(appender.list.getFirst().getFormattedMessage().contains("Bearer user:alice"));
		} finally {
			detachAppender(appender);
		}
	}

	@Test
	void logsRequiredFieldsForNonReservationRequests() throws Exception {
		ListAppender<ILoggingEvent> appender = attachAppender();
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/health/live");
		MockHttpServletResponse response = new MockHttpServletResponse();

		try {
			filter.doFilter(request, response, (servletRequest, servletResponse) -> { });

			Map<String, Object> fields = fieldsFrom(appender.list.getFirst());
			assertEquals("GET", fields.get("method"));
			assertEquals("/health/live", fields.get("path"));
			assertEquals(200, fields.get("status"));
			assertTrue(fields.get("duration_ms") instanceof Number);
			assertFalse(fields.containsKey("user_id"));
			assertFalse(fields.containsKey("show_id"));
		} finally {
			detachAppender(appender);
		}
	}

	private ListAppender<ILoggingEvent> attachAppender() {
		Logger requestLogger = (Logger) LoggerFactory.getLogger(RequestLogFilter.class);
		ListAppender<ILoggingEvent> appender = new ListAppender<>();
		appender.start();
		requestLogger.addAppender(appender);
		return appender;
	}

	private void detachAppender(ListAppender<ILoggingEvent> appender) {
		((Logger) LoggerFactory.getLogger(RequestLogFilter.class)).detachAppender(appender);
		appender.stop();
	}

	private Map<String, Object> fieldsFrom(ILoggingEvent event) {
		return event.getKeyValuePairs().stream()
				.collect(Collectors.toMap(pair -> pair.key, pair -> pair.value));
	}
}
