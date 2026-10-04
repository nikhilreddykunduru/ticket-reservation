# Ticket Reservation

A seat-reservation app with a web interface and REST API, backed by PostgreSQL.

**Live app:** [ticket-reservation-nqdb.onrender.com](https://ticket-reservation-nqdb.onrender.com/)

## Run locally

Requires Java 21 and Docker.

```bash
docker compose up --build
```

Open [http://localhost:8080](http://localhost:8080). To stop the app and database, run `docker compose down`.

## API

All API routes are served from the app URL. User routes require `Authorization: Bearer user:<your-user-id>`; show creation requires the configured admin token as a Bearer token.

| Method | Route | Description |
| --- | --- | --- |
| `POST` | `/shows` | Create a show (admin) |
| `GET` | `/shows/{id}` | Get show and seat availability |
| `POST` | `/shows/{id}/reserve` | Reserve seats (user) |
| `POST` | `/reservations/{id}/cancel` | Cancel a reservation (user) |
| `GET` | `/health/ready` | Check app and database readiness |

Reservations accept an optional `Idempotency-Key` header. The admin token is configured with `ADMIN_TOKEN`; set a private value in your environment and never use the development default in production.

## Metrics and logs

### Metrics

The app exposes Prometheus-format metrics at `/metrics`. With the app running locally, view them with:

```bash
curl http://localhost:8080/metrics
```

For the hosted app, open [https://ticket-reservation-nqdb.onrender.com/metrics](https://ticket-reservation-nqdb.onrender.com/metrics). A Prometheus server can scrape this URL. The endpoint includes reservation confirmation, decline and replay counters, plus the current available-seat gauge.

### Logs

When running locally with Docker Compose, view the app's structured JSON logs with:

```bash
docker compose logs -f app
```

Press `Ctrl+C` to stop following the logs. For the hosted app, open the service in the Render dashboard and select its **Logs** view. Request log entries include the request ID, HTTP method, path, status, and duration; reservation requests also include the user and show IDs.

## Burst test

Run `scripts/burst.sh <BASE_URL> [HOT_SEAT_REQUESTS]` to exercise concurrent reservations. The hot-seat request count defaults to `500`; pass a positive integer to change it, for example:

```bash
bash scripts/burst.sh http://localhost:8080 1000
```

The index page also has a **Hot-seat request count** field, defaulting to `500`, that controls the hot-seat storm when you run the burst test from the browser. The idempotency and per-user-limit test groups remain fixed at 100 and 10 requests, respectively.
