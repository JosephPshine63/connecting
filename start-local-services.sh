#!/usr/bin/env bash
# Starts the 5 non-Docker services (backend, file-service, notification-service,
# api-gateway, frontend) in the background, each logging to logs/<name>.log.
# Run deploy-local.sh first to bring up Postgres/Keycloak/RabbitMQ.
#
# Usage:
#   ./start-local-services.sh          # start all services
#   ./start-local-services.sh stop     # stop all services started by this script
#   ./start-local-services.sh status   # show which services are running

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LOG_DIR="$SCRIPT_DIR/logs"
PID_FILE="$SCRIPT_DIR/.local-services.pids"

log() { echo "[$(date '+%H:%M:%S')] $*"; }
ok()  { echo "[$(date '+%H:%M:%S')] ✓ $*"; }
err() { echo "[$(date '+%H:%M:%S')] ✗ $*" >&2; exit 1; }

command -v direnv >/dev/null 2>&1 || err "direnv not found — needed to load .env (see .envrc)"
[[ -f "$SCRIPT_DIR/.env" ]] || err ".env not found — create it from .env.example"

# name:dir:command
SERVICES=(
  "backend:wac/backend:./mvnw spring-boot:run"
  "file-service:wac/file-service:./mvnw spring-boot:run"
  "notification-service:wac/notification-service:./mvnw spring-boot:run"
  "api-gateway:wac/api-gateway:./mvnw spring-boot:run"
  "frontend:wac/frontend:npm start"
)

start_all() {
  mkdir -p "$LOG_DIR"
  : > "$PID_FILE"

  for entry in "${SERVICES[@]}"; do
    local_name="${entry%%:*}"
    rest="${entry#*:}"
    local_dir="${rest%%:*}"
    local_cmd="${rest#*:}"

    log "Starting $local_name ..."
    (
      cd "$SCRIPT_DIR/$local_dir"
      exec direnv exec "$SCRIPT_DIR/$local_dir" $local_cmd
    ) > "$LOG_DIR/$local_name.log" 2>&1 &
    pid=$!
    echo "$local_name:$pid" >> "$PID_FILE"
    ok "$local_name started (pid $pid, log: logs/$local_name.log)"
    sleep 1
  done

  echo ""
  echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
  echo " All services launching in the background."
  echo " Tail logs with:  tail -f logs/<service>.log"
  echo " Check status:    ./start-local-services.sh status"
  echo " Stop everything: ./start-local-services.sh stop"
  echo ""
  echo " Gateway  : http://localhost:8081"
  echo " Frontend : http://localhost:4200"
  echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
}

stop_all() {
  [[ -f "$PID_FILE" ]] || { log "No PID file found — nothing to stop"; exit 0; }
  while IFS=: read -r name pid; do
    [[ -z "${pid:-}" ]] && continue
    if kill -0 "$pid" 2>/dev/null; then
      pkill -TERM -P "$pid" 2>/dev/null || true
      kill -TERM "$pid" 2>/dev/null || true
      ok "Stopped $name (pid $pid)"
    else
      log "$name (pid $pid) already stopped"
    fi
  done < "$PID_FILE"
  rm -f "$PID_FILE"
}

status_all() {
  [[ -f "$PID_FILE" ]] || { log "No PID file found — nothing tracked"; exit 0; }
  while IFS=: read -r name pid; do
    [[ -z "${pid:-}" ]] && continue
    if kill -0 "$pid" 2>/dev/null; then
      echo "  $name: running (pid $pid)"
    else
      echo "  $name: stopped"
    fi
  done < "$PID_FILE"
}

case "${1:-start}" in
  start)  start_all ;;
  stop)   stop_all ;;
  status) status_all ;;
  *) err "Usage: $0 [start|stop|status]" ;;
esac
