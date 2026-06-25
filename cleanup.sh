#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
COMPOSE_DIR="wac/docker-compose-connecting"

echo "[cleanup] Stopping all running containers..."
docker stop $(docker ps -q) 2>/dev/null || echo "[cleanup] No running containers."

echo "[cleanup] Removing all containers..."
docker rm -f $(docker ps -aq) 2>/dev/null || echo "[cleanup] No containers to remove."

echo "[cleanup] Removing project networks..."
(cd "$SCRIPT_DIR/$COMPOSE_DIR" && docker compose down --remove-orphans 2>/dev/null) || true

echo "[cleanup] Pruning unused images, networks, and build cache..."
docker system prune -af

echo "[cleanup] Done. Docker is clean."
docker ps -a
