# Reservation design

## Atomic decision

Seat allocation is decided inside one PostgreSQL transaction. The service
locks the user's show-counter row with `SELECT ... FOR UPDATE`, checks the
per-user limit while holding that lock, then locks every requested seat row
with `SELECT ... FOR UPDATE` in deterministic `seat_code` order. It verifies
that all requested seats exist and are available before it creates the
reservation, links and confirms the seats, increments the user's occupied
count, and stores the idempotency result. These writes commit together or roll
back together.

The seat-row locks serialize competing requests for the same seat: only one
transaction can hold a given seat lock at a time, and a later transaction
checks the committed seat status after it acquires the lock. Multi-seat
requests acquire seat locks in the same order, avoiding deadlocks caused by
overlapping seat sets being locked in opposite orders. Reservation and
cancellation flows acquire the user's counter before seat locks.

## Idempotency

The idempotency key and its request hash/result are stored in PostgreSQL's
`idempotency_keys` table, scoped to `(show_id, user_id, idempotency_key)`.
The table has a unique constraint on that tuple. The counter-row lock
serializes requests for the same user and show; the service checks for a
matching key again after acquiring that lock. The unique constraint is the
database-level backstop. A successful reservation and its idempotency record
are committed in the same transaction, so a committed key has one reservation
effect and retries can return that result.

The request body is canonicalized by sorting the seat codes and hashed with
SHA-256. Repeating a key with the same seat list replays the original result;
using it with a different seat list returns HTTP 409
`idempotency_conflict`. A failed/rolled-back attempt does not leave a key
record. This does not guarantee exactly-once network delivery: a client may
not receive the response even though the transaction committed, and must
retry with the same key to retrieve the result.

## Holds and expiry

The application does not create temporary holds or expire them. It confirms
seats directly within the reservation transaction. Cancellation releases the
confirmed seats and decrements the user's occupied count in a transaction.

## Consistency and availability

PostgreSQL is the source of truth for seat status, counters, reservations, and
idempotency records. Reservation writes require a successful database
transaction. If the application cannot reach the database, it cannot safely
decide whether a seat is available and should fail the request rather than
accept a potentially conflicting booking. This favors consistency over
availability during a database partition.

## Observability and paging

The application exposes `/metrics` with reservation-confirmed, declined (by
reason), and replay counters, plus the current available-seat gauge. Request
logs are structured and include request ID, method, path, status, and
duration; reservation requests also include user and show IDs. The readiness
endpoint checks database availability.

At 2am, I would want alerts for sustained readiness failures, elevated 5xx
responses or timeouts, and database connectivity or saturation problems.
I would also watch for a sharp increase in `seat_taken` and
`per_user_limit` declines as possible signs of a customer-impacting issue.
Today the app provides counters and an availability gauge, but no latency or
database-saturation metrics and no alert rules; those require external
monitoring configuration.

## AI usage

AI assisted the writing of code, review, schema, test code, and helped draft this write-up.  

## What I would do next

I would add and test payment-backed holds only if checkout requires a
reservation window, with explicit expiry and safe handling of payment
callbacks racing expiry. I would also define idempotency-key retention, configure
actionable alerts, and test database outage and recovery behavior.
