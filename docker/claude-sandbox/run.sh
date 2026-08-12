#!/usr/bin/env bash
# Runs Claude Code in an isolated container with only this repo mounted in —
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

docker run -it --rm \
  --add-host=host.docker.internal:host-gateway \
  -v "$REPO_ROOT:/workspace" \
  -v "value-screener-claude-sandbox-home:/root/.claude" \
  -e ADMIN_USERNAME \
  -e ADMIN_PASSWORD \
  -e BACKEND_URL=http://host.docker.internal:8080 \
  "$IMAGE" "$@"
