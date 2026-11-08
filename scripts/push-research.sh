#!/usr/bin/env bash
# Pushes ONE reviewed research snapshot from the local backend to the live backend.
# Runs on the host only (never in the sandbox). See README.md
# "Recherchedaten ins Live-System übertragen".
#
# Usage: scripts/push-research.sh <ISIN>
#
# Tokens come from ~/.config/value-screener/live.env (chmod 600, outside the repo) and are passed to
# curl via process substitution (printf is a bash builtin), so they never appear in argv.
# Never use `set -x` here: it would print the tokens.
set -euo pipefail

if [[ "${VALUE_SCREENER_SANDBOX:-}" == "1" ]]; then
  echo "Refusing to run inside the sandbox: push-research.sh must run on the host (it handles the live token)." >&2
  exit 1
fi

if [[ $# -ne 1 ]]; then
  echo "Usage: $0 <ISIN>" >&2
  exit 1
fi

ISIN="$(printf '%s' "$1" | tr -d '[:space:]' | tr '[:lower:]' '[:upper:]')"
if [[ ! "$ISIN" =~ ^[A-Z]{2}[A-Z0-9]{9}[0-9]$ ]]; then
  echo "Invalid ISIN (expected 2 letters, 9 letters/digits, 1 digit)." >&2
  exit 1
fi

ENV_FILE="${HOME}/.config/value-screener/live.env"
if [[ ! -f "$ENV_FILE" ]]; then
  echo "Missing $ENV_FILE (see README: Recherchedaten ins Live-System übertragen)." >&2
  exit 1
fi
# Linux (GNU stat) first, BSD/macOS stat as fallback.
MODE="$(stat -c '%a' "$ENV_FILE" 2>/dev/null || stat -f '%Lp' "$ENV_FILE")"
OWNER="$(stat -c '%u' "$ENV_FILE" 2>/dev/null || stat -f '%u' "$ENV_FILE")"
if [[ ! "$MODE" =~ ^[0-7]+$ ]] || (( (8#$MODE & 8#077) != 0 )); then
  echo "$ENV_FILE must not be accessible by group or others (run: chmod 600 $ENV_FILE)." >&2
  exit 1
fi
if [[ "$OWNER" != "$(id -u)" ]]; then
  echo "$ENV_FILE must be owned by the current user." >&2
  exit 1
fi

if grep -q $'\r' "$ENV_FILE"; then
  echo "$ENV_FILE has Windows line endings (CRLF); convert it to LF (e.g. dos2unix or re-save in nano)." >&2
  exit 1
fi

# shellcheck disable=SC1090
source "$ENV_FILE"

: "${LIVE_BACKEND_URL:?LIVE_BACKEND_URL missing in live.env}"
: "${RESEARCH_SYNC_TOKEN:?RESEARCH_SYNC_TOKEN missing in live.env}"
: "${RESEARCH_INGEST_TOKEN:?RESEARCH_INGEST_TOKEN missing in live.env}"

if [[ "$RESEARCH_SYNC_TOKEN" == "$RESEARCH_INGEST_TOKEN" ]]; then
  echo "RESEARCH_SYNC_TOKEN and RESEARCH_INGEST_TOKEN must differ (separate tokens per endpoint)." >&2
  exit 1
fi

LIVE_URL="${LIVE_BACKEND_URL%/}"
LOCAL_URL="${LOCAL_BACKEND_URL:-http://localhost:8080}"
LOCAL_URL="${LOCAL_URL%/}"

# https anywhere; plain http only for localhost / 127.0.0.1 with an explicit port.
URL_OK='^(https://[A-Za-z0-9.-]+(:[0-9]+)?|http://(localhost|127\.0\.0\.1):[0-9]+)$'
if [[ ! "$LIVE_URL" =~ $URL_OK ]]; then
  echo "LIVE_BACKEND_URL must start with https:// (http only for localhost/127.0.0.1 with a port)." >&2
  exit 1
fi
if [[ ! "$LOCAL_URL" =~ $URL_OK ]]; then
  echo "LOCAL_BACKEND_URL must start with https:// (http only for localhost/127.0.0.1 with a port)." >&2
  exit 1
fi

command -v curl >/dev/null || { echo "curl is required." >&2; exit 1; }

WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

# Prints only the HTTP status code; the response body goes to the given file and is never printed.
http_status() {
  local token="$1" out="$2"
  shift 2
  curl -q -sS --max-time 30 -o "$out" -w '%{http_code}' \
    -H @<(printf 'Authorization: Bearer %s\n' "$token") "$@" || true
}

EXPORT_FILE="$WORK/export.json"
CODE="$(http_status "$RESEARCH_INGEST_TOKEN" "$EXPORT_FILE" \
  "$LOCAL_URL/api/research/ingest/snapshots/$ISIN/export")"
echo "ISIN $ISIN: export from local backend -> HTTP ${CODE:-000}"
if [[ ! "$CODE" =~ ^2[0-9][0-9]$ ]]; then
  echo "Export failed (404 = unknown ISIN, 409 = no group assigned, 401/000 = token or backend problem)." >&2
  exit 1
fi

CODE="$(http_status "$RESEARCH_SYNC_TOKEN" "$WORK/response.json" \
  -X POST -H 'Content-Type: application/json' --data-binary @"$EXPORT_FILE" \
  "$LIVE_URL/api/research/sync/snapshots")"
echo "ISIN $ISIN: import into live backend -> HTTP ${CODE:-000}"
if [[ ! "$CODE" =~ ^2[0-9][0-9]$ ]]; then
  echo "Import failed (422 = group missing live, 401 = wrong sync token, 404 = sync disabled live)." >&2
  exit 1
fi
echo "ISIN $ISIN: pushed."
