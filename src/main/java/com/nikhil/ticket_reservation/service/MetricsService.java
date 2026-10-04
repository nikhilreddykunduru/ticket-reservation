package com.nikhil.ticket_reservation.service;

import java.util.Locale;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class MetricsService {

	private static final String[] DECLINE_REASONS = {
			"seat_taken", "per_user_limit", "idempotency_conflict"
	};

	private final JdbcTemplate jdbcTemplate;
	private final MeterRegistry meterRegistry;

	public MetricsService(JdbcTemplate jdbcTemplate, MeterRegistry meterRegistry) {
		this.jdbcTemplate = jdbcTemplate;
		this.meterRegistry = meterRegistry;
	}

	public String scrape() {
		StringBuilder metrics = new StringBuilder();
		appendCounter(metrics, "reservations_confirmed_total");
		metrics.append("# TYPE reservations_declined_total counter\n");
		for (String reason : DECLINE_REASONS) {
			metrics.append("reservations_declined_total{reason=\"")
					.append(reason)
					.append("\"} ")
					.append(counterValue("reservations_declined_total", "reason", reason))
					.append('\n');
		}
		appendCounter(metrics, "reservations_replayed_total");
		metrics.append("# TYPE seats_available gauge\n")
				.append("seats_available ")
				.append(availableSeatCount())
				.append('\n');
		return metrics.toString();
	}

	private void appendCounter(StringBuilder metrics, String name) {
		metrics.append("# TYPE ").append(name).append(" counter\n")
				.append(name).append(' ')
				.append(counterValue(name))
				.append('\n');
	}

	private String counterValue(String name, String... tags) {
		return String.format(Locale.ROOT, "%.0f", meterRegistry.counter(name, tags).count());
	}

	private long availableSeatCount() {
		return jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM seats WHERE status = 'AVAILABLE'",
				Long.class);
	}
}
