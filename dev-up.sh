#!/usr/bin/env bash
# Starts Postgres, backend, and frontend together for local development.
# Stop with Ctrl+C — backend and frontend are stopped, Postgres keeps running.
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")"

if [[ ! -f .env ]]; then
  echo "Missing .env. Copy .env.example to .env and fill in ADMIN_PASSWORD_HASH first:"
  echo "  cp .env.example .env"
  exit 1
fi

set -a
source .env
set +a

if [[ -z "${ADMIN_PASSWORD_HASH:-}" ]]; then
  echo "ADMIN_PASSWORD_HASH is not set in .env. Generate it first (see .env.example) and fill it in."
  exit 1
fi

mkdir -p logs

echo "==> Starting Postgres (docker compose)"
docker compose up -d

echo "==> Waiting for Postgres to accept connections"
until docker compose exec -T postgres pg_isready -U value_screener >/dev/null 2>&1; do
  sleep 1
done
echo "    Postgres is ready."

echo "==> Starting backend (logs/backend.log)"
(cd backend && mvn -q spring-boot:run) >logs/backend.log 2>&1 &
BACKEND_PID=$!

echo "==> Waiting for backend on :8080"
until (exec 3<>/dev/tcp/127.0.0.1/8080) 2>/dev/null; do
  if ! kill -0 "$BACKEND_PID" 2>/dev/null; then
    echo "Backend process exited early — check logs/backend.log"
    exit 1
  fi
  sleep 1
done
exec 3<&- 3>&- 2>/dev/null || true
echo "    Backend is up on http://localhost:8080"

echo "==> Starting frontend (logs/frontend.log)"
(cd frontend && npm run dev) >logs/frontend.log 2>&1 &
FRONTEND_PID=$!

cleanup() {
  echo ""
  echo "==> Stopping backend and frontend (Postgres keeps running — 'docker compose down' to stop it too)"
  kill "$BACKEND_PID" "$FRONTEND_PID" 2>/dev/null || true
  wait "$BACKEND_PID" "$FRONTEND_PID" 2>/dev/null || true
}
trap cleanup EXIT INT TERM

echo ""
echo "Frontend: http://localhost:5173"
echo "Backend:  http://localhost:8080"
echo "Login: ADMIN_USERNAME=$ADMIN_USERNAME, password = whatever you hashed into ADMIN_PASSWORD_HASH"
echo "Logs: tail -f logs/backend.log logs/frontend.log"
echo "Press Ctrl+C to stop backend + frontend."
echo ""

wait "$BACKEND_PID" "$FRONTEND_PID"
