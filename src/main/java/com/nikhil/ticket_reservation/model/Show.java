package com.nikhil.ticket_reservation.model;

import java.util.UUID;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Table("shows")
public record Show(
		@Id UUID id,
		String name,
		@Column("price_paise") long pricePaise,
		@Column("per_user_limit") int perUserLimit) {
}
