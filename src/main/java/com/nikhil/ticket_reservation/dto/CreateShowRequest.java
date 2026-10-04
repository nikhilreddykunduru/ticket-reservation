package com.nikhil.ticket_reservation.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

public record CreateShowRequest(
		@NotBlank String name,
		@NotEmpty List<@NotBlank String> seats,
		@NotNull @PositiveOrZero @JsonProperty("price_paise") @JsonDeserialize(using = IntegerLongDeserializer.class) Long pricePaise,
		@NotNull @Positive @JsonProperty("per_user_limit") Integer perUserLimit) {

	static class IntegerLongDeserializer extends ValueDeserializer<Long> {

		@Override
		public Long deserialize(JsonParser parser, DeserializationContext context) {
			if (!parser.hasToken(JsonToken.VALUE_NUMBER_INT)) {
				return (Long) context.handleUnexpectedToken(Long.class, parser);
			}
			return parser.getLongValue();
		}
	}
}
