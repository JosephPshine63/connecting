#!/usr/bin/env bash
# Remove Docker images built/used for this project: the custom app images
# (backend, frontend, file-service, api-gateway, notification-service,
# call-service) and the observability stack (prometheus, grafana, loki,
# tempo) — any tag, any
# registry prefix. Leaves postgres/keycloak/rabbitmq and everything else on
# the host untouched — unlike the old cleanup.sh, which stopped/removed ALL
# containers and pruned the whole Docker host.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

log() { echo "[$(date '+%H:%M:%S')] $*"; }
ok()  { echo "[$(date '+%H:%M:%S')] ✓ $*"; }

CONTAINERS=(
  wacchat-backend wacchat-frontend wacchat-file-service wacchat-api-gateway wacchat-notification-service wacchat-call-service
  wacchat-prometheus wacchat-grafana wacchat-loki wacchat-tempo
)
IMAGE_PATTERNS=(
  wacchat-backend wacchat-frontend wacchat-file-service wacchat-api-gateway wacchat-notification-service wacchat-call-service
  prom/prometheus grafana/grafana grafana/loki grafana/tempo
)
PATTERN=$(IFS='|'; echo "${IMAGE_PATTERNS[*]}")

mapfile -t MATCHES < <(docker images --format '{{.Repository}}:{{.Tag}} {{.ID}}' | grep -E "($PATTERN)" || true)

if [[ ${#MATCHES[@]} -eq 0 ]]; then
  log "No matching images found."
  exit 0
fi

echo "The following images will be removed:"
printf '  %s\n' "${MATCHES[@]}"
read -rp "Proceed? [y/N] " confirm
[[ "$confirm" =~ ^[Yy]$ ]] || { log "Aborted."; exit 0; }

for name in "${CONTAINERS[@]}"; do
  if docker ps -a --format '{{.Names}}' | grep -qx "$name"; then
    log "Removing container: $name"
    docker rm -f "$name" >/dev/null
  fi
done

for entry in "${MATCHES[@]}"; do
  id="${entry##* }"
  ref="${entry% *}"
  log "Removing image $ref ($id)..."
  docker rmi -f "$id" 2>/dev/null || true
done

ok "Done."
docker images | grep -E "($PATTERN)" || echo "No matching images remain."

log "Pulling latest changes from origin..."
command -v git >/dev/null 2>&1 || { echo "[ERROR] git not found in PATH"; exit 1; }
git -C "$SCRIPT_DIR" pull --ff-only origin main
ok "Repository is up to date"
