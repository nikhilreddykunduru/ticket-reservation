CREATE TABLE shows (
    id UUID PRIMARY KEY,
    name TEXT NOT NULL,
    price_paise BIGINT NOT NULL CHECK (price_paise >= 0),
    per_user_limit INTEGER NOT NULL CHECK (per_user_limit > 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE reservations (
    id UUID PRIMARY KEY,
    show_id UUID NOT NULL REFERENCES shows (id),
    user_id TEXT NOT NULL,
    amount_paise BIGINT NOT NULL CHECK (amount_paise >= 0),
    status TEXT NOT NULL CHECK (status IN ('CONFIRMED', 'CANCELLED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    cancelled_at TIMESTAMPTZ
);

CREATE TABLE seats (
    id UUID PRIMARY KEY,
    show_id UUID NOT NULL REFERENCES shows (id),
    seat_code TEXT NOT NULL,
    status TEXT NOT NULL DEFAULT 'AVAILABLE'
        CHECK (status IN ('AVAILABLE', 'CONFIRMED')),
    reservation_id UUID REFERENCES reservations (id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (show_id, seat_code)
);

CREATE TABLE reservation_seats (
    reservation_id UUID NOT NULL REFERENCES reservations (id),
    seat_id UUID NOT NULL REFERENCES seats (id),
    PRIMARY KEY (reservation_id, seat_id)
);

CREATE TABLE idempotency_keys (
    id UUID PRIMARY KEY,
    show_id UUID NOT NULL REFERENCES shows (id),
    user_id TEXT NOT NULL,
    idempotency_key TEXT NOT NULL,
    request_hash TEXT NOT NULL,
    reservation_id UUID REFERENCES reservations (id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (show_id, user_id, idempotency_key)
);

CREATE TABLE user_show_counters (
    show_id UUID NOT NULL REFERENCES shows (id),
    user_id TEXT NOT NULL,
    occupied_count INTEGER NOT NULL DEFAULT 0 CHECK (occupied_count >= 0),
    PRIMARY KEY (show_id, user_id)
);
