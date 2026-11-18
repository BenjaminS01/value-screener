#!/usr/bin/env bash
# Runs Claude Code in an isolated container with only this repo mounted in (read-only) —
# not the host's home directory. See README.md "Sandbox für Web-Recherche-Skills".
#
# Logs in on its own (first run only) instead of reusing the host's real
# ~/.claude/.credentials.json — that file is never mounted in, so a
# compromised sandbox session can't exfiltrate the real Claude Code login.
# The sandbox's own login persists across runs in the named Docker volume
# "value-screener-claude-sandbox-home"; revoking it never touches the real one.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(git -C "$SCRIPT_DIR" rev-parse --show-toplevel)"
IMAGE=value-screener-claude-sandbox

docker build -t "$IMAGE" "$SCRIPT_DIR"

# Rootless Docker (slirp4netns with host loopback disabled): "host-gateway" points into rootlesskit's own
# network namespace, not to this machine, so the backend is unreachable. Use this machine's own address
# instead (the backend listens on all interfaces). Rootful Docker / Docker Desktop keep host-gateway.
if docker info -f '{{json .SecurityOptions}}' | grep -q 'name=rootless'; then
  HOST_ADDR="$(ip -4 route get 1.1.1.1 | awk '{for (i = 1; i < NF; i++) if ($i == "src") { print $(i + 1); exit }}')"
  if [[ -z "$HOST_ADDR" ]]; then
    echo "Could not determine this machine's IPv4 address for host.docker.internal." >&2
    exit 1
  fi
else
  HOST_ADDR=host-gateway
fi

docker run -it --rm \
  --add-host="host.docker.internal:$HOST_ADDR" \
  -v "$REPO_ROOT:/workspace:ro" \
  -v "value-screener-claude-sandbox-home:/root/.claude" \
  -e RESEARCH_INGEST_TOKEN \
  -e VALUE_SCREENER_SANDBOX=1 \
  -e BACKEND_URL=http://host.docker.internal:8080 \
  "$IMAGE" "$@"
