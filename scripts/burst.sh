#!/usr/bin/env bash
set -euo pipefail

HOT_SEAT_REQUESTS=500
IDEMPOTENCY_REQUESTS=100
USER_LIMIT_REQUESTS=10
MAX_PARALLEL_REQUESTS=50
BASE_URL=${1:-}
ADMIN_TOKEN=${ADMIN_TOKEN:-admin-secret}

if [[ -z "$BASE_URL" ]]; then
	printf 'Usage: %s <BASE_URL>\n' "$0" >&2
	exit 2
fi

if ! command -v curl >/dev/null 2>&1 || ! command -v jq >/dev/null 2>&1; then
	printf 'This script requires curl and jq.\n' >&2
	exit 2
fi

while [[ "$BASE_URL" == */ ]]; do
	BASE_URL=${BASE_URL%/}
done

if [[ ! "$BASE_URL" =~ ^https?:// ]]; then
	printf 'BASE_URL must start with http:// or https://.\n' >&2
	exit 2
fi

WORK_DIR=$(mktemp -d)
cleanup() {
	rm -rf "$WORK_DIR"
}
trap cleanup EXIT

show_name="burst-$(date +%s)-$$"
seats_json=$(jq -cn '["A12", "B1"] + [range(1; 11) | "C\(.)"]')
show_request=$(jq -cn \
	--arg name "$show_name" \
	--argjson seats "$seats_json" \
	'{name: $name, seats: $seats, price_paise: 25000, per_user_limit: 4}')

create_status=$(curl -sS --connect-timeout 10 --max-time 60 \
	-o "$WORK_DIR/create.json" -w '%{http_code}' \
	-H "Authorization: Bearer $ADMIN_TOKEN" \
	-H 'Content-Type: application/json' \
	-d "$show_request" "$BASE_URL/shows") || {
	printf 'Could not create a show at %s/shows.\n' "$BASE_URL" >&2
	exit 1
}

if [[ "$create_status" != "201" ]]; then
	printf 'Show creation failed (HTTP %s): ' "$create_status" >&2
	jq -c . "$WORK_DIR/create.json" >&2 2>/dev/null || cat "$WORK_DIR/create.json" >&2
	exit 1
fi

show_id=$(jq -er '.id | strings | select(length > 0)' "$WORK_DIR/create.json") || {
	printf 'Show creation response did not contain an id.\n' >&2
	exit 1
}

request_reservation() {
	local group=$1
	local index=$2
	local seat=$3
	local user_id=$4
	local idempotency_key=$5
	local request_file="$WORK_DIR/request-$group-$index.json"
	local body_file="$WORK_DIR/body-$group-$index.json"
	local status_file="$WORK_DIR/status-$group-$index"
	local status

	jq -cn --arg seat "$seat" '{seats: [$seat]}' > "$request_file"
	status=$(curl -sS --connect-timeout 10 --max-time 60 \
		-o "$body_file" -w '%{http_code}' \
		-H "Authorization: Bearer user:$user_id" \
		-H "Idempotency-Key: $idempotency_key" \
		-H 'Content-Type: application/json' \
		-d "@$request_file" "$BASE_URL/shows/$show_id/reserve") || status=000
	printf '%s\n' "$status" > "$status_file"
}

run_hot_seat_storm() {
	local index
	local pending=0
	for ((index = 1; index <= HOT_SEAT_REQUESTS; index++)); do
		request_reservation hot "$index" A12 "burst-hot-$index" "hot-$show_id-$index" &
		pending=$((pending + 1))
		if ((pending >= MAX_PARALLEL_REQUESTS)); then
			wait
			pending=0
		fi
	done
	if ((pending > 0)); then
		wait
	fi
}

run_idempotency_storm() {
	local index
	local pending=0
	for ((index = 1; index <= IDEMPOTENCY_REQUESTS; index++)); do
		request_reservation idempotency "$index" B1 burst-idempotent "same-key-$show_id" &
		pending=$((pending + 1))
		if ((pending >= MAX_PARALLEL_REQUESTS)); then
			wait
			pending=0
		fi
	done
	if ((pending > 0)); then
		wait
	fi
}

run_user_limit_storm() {
	local index
	local pending=0
	for ((index = 1; index <= USER_LIMIT_REQUESTS; index++)); do
		request_reservation user-limit "$index" "C$index" burst-limited-user "limit-$show_id-$index" &
		pending=$((pending + 1))
		if ((pending >= MAX_PARALLEL_REQUESTS)); then
			wait
			pending=0
		fi
	done
	if ((pending > 0)); then
		wait
	fi
}

printf '========================================\n'
printf 'SEAT RESERVATION BURST TEST\n'
printf '========================================\n\n'
printf 'Base URL:\n%s\n\n' "$BASE_URL"
printf 'Show:\n%s\n\n' "$show_id"
printf 'Hot seat:\nA12\n\n'
printf 'Requests:\nHot seat: %s\nIdempotency: %s\nUser limit: %s\n\n' \
	"$HOT_SEAT_REQUESTS" "$IDEMPOTENCY_REQUESTS" "$USER_LIMIT_REQUESTS"

run_hot_seat_storm
run_idempotency_storm
run_user_limit_storm

confirmed=0
seat_taken=0
user_limit=0
idempotent=0
server_errors=0
unexpected=0

classify_group() {
	local group=$1
	local count=$2
	local index
	local status
	local error
	local reference_body=''
	local current_body

	for ((index = 1; index <= count; index++)); do
		status=$(<"$WORK_DIR/status-$group-$index")
		case "$status" in
			201)
				case "$group" in
					hot|user-limit)
						confirmed=$((confirmed + 1))
						;;
					idempotency)
						if ((index == 1)); then
							confirmed=$((confirmed + 1))
							reference_body=$(jq -cS . "$WORK_DIR/body-$group-$index.json")
						else
							current_body=$(jq -cS . "$WORK_DIR/body-$group-$index.json")
							if [[ "$current_body" != "$reference_body" ]]; then
								printf 'Idempotency replay %s returned a different response.\n' "$index" >&2
								unexpected=$((unexpected + 1))
							else
								idempotent=$((idempotent + 1))
							fi
						fi
						;;
				esac
				;;
			409)
				error=$(jq -r '.error // empty' "$WORK_DIR/body-$group-$index.json" 2>/dev/null || true)
				case "$error" in
					seat_taken) seat_taken=$((seat_taken + 1)) ;;
					per_user_limit) user_limit=$((user_limit + 1)) ;;
					idempotency_conflict)
						printf 'Unexpected idempotency conflict in request %s.\n' "$index" >&2
						unexpected=$((unexpected + 1))
						;;
					*)
						printf 'Unexpected HTTP 409 in %s request %s: %s\n' \
							"$group" "$index" "$error" >&2
						unexpected=$((unexpected + 1))
						;;
				esac
				;;
			5??)
				server_errors=$((server_errors + 1))
				;;
			000)
				printf 'Transport failure in %s request %s.\n' "$group" "$index" >&2
				unexpected=$((unexpected + 1))
				;;
			*)
				printf 'Unexpected HTTP %s in %s request %s.\n' "$status" "$group" "$index" >&2
				unexpected=$((unexpected + 1))
				;;
		esac
	done
}

classify_group hot "$HOT_SEAT_REQUESTS"
classify_group idempotency "$IDEMPOTENCY_REQUESTS"
classify_group user-limit "$USER_LIMIT_REQUESTS"

show_status=$(curl -sS --connect-timeout 10 --max-time 60 \
	-o "$WORK_DIR/show.json" -w '%{http_code}' "$BASE_URL/shows/$show_id") || {
	printf 'Could not fetch final show state.\n' >&2
	exit 1
}
if [[ "$show_status" != "200" ]]; then
	printf 'Fetching final show state failed (HTTP %s): ' "$show_status" >&2
	jq -c . "$WORK_DIR/show.json" >&2 2>/dev/null || cat "$WORK_DIR/show.json" >&2
	exit 1
fi

total=$(jq -er '.total_seats | numbers' "$WORK_DIR/show.json")
available=$(jq -er '.available_seats | numbers' "$WORK_DIR/show.json")
held=$(jq -er '.held_seats | numbers' "$WORK_DIR/show.json")
confirmed_seats=$(jq -er '.confirmed_seats | numbers' "$WORK_DIR/show.json")

printf 'Results\n-------\n'
printf '%-21s: %s\n' '201 confirmed' "$confirmed"
printf '%-21s: %s\n' '409 seat_taken' "$seat_taken"
printf '%-21s: %s\n' '409 user_limit' "$user_limit"
printf '%-21s: %s\n' '201 idempotent replay' "$idempotent"
printf '%-21s: %s\n' '5xx' "$server_errors"
printf '%-21s: %s\n' 'Unexpected responses' "$unexpected"

printf '\nFinal state\n-----------\n'
printf '%-21s: %s\n' 'Total seats' "$total"
printf '%-21s: %s\n' 'Available' "$available"
printf '%-21s: %s\n' 'Held' "$held"
printf '%-21s: %s\n' 'Confirmed' "$confirmed_seats"

printf '\nReconciliation\n--------------\n'
printf '%s + %s + %s = %s\n' "$available" "$held" "$confirmed_seats" "$total"

expected_confirmed=$((1 + 1 + 4))
expected_seat_taken=$((HOT_SEAT_REQUESTS - 1))
expected_user_limit=$((USER_LIMIT_REQUESTS - 4))
expected_idempotent=$((IDEMPOTENCY_REQUESTS - 1))
if ((available + held + confirmed_seats == total \
	&& confirmed_seats == expected_confirmed \
	&& confirmed == expected_confirmed \
	&& seat_taken == expected_seat_taken \
	&& user_limit == expected_user_limit \
	&& idempotent == expected_idempotent \
	&& server_errors == 0 \
	&& unexpected == 0)); then
	printf '\nPASS\n'
else
	printf '\nFAIL\n'
	exit 1
fi
