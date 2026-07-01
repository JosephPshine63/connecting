#!/usr/bin/env bash
# Local development — starts PostgreSQL + Keycloak in Docker.
# Run backend and frontend manually (see summary at the end).
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

log() { echo "[$(date '+%H:%M:%S')] $*"; }
ok()  { echo "[$(date '+%H:%M:%S')] ✓ $*"; }
err() { echo "[$(date '+%H:%M:%S')] ✗ $*" >&2; exit 1; }

command -v docker >/dev/null 2>&1 || err "Docker not found"
docker info       >/dev/null 2>&1 || err "Docker daemon is not running"

set_keycloak_admin_email() {
  [[ -z "${ADMIN_EMAIL:-}"              ]] && return 0
  [[ -z "${KEYCLOAK_ADMIN_USERNAME:-}"  ]] && return 0
  [[ -z "${KEYCLOAK_ADMIN_PASSWORD:-}"  ]] && return 0
  local kc="http://localhost:8180"
  log "Waiting for Keycloak to be ready..."
  local i=0
  until curl -sf "$kc/realms/master" >/dev/null 2>&1; do
    (( ++i )); if (( i >= 30 )); then log "Keycloak not ready — skipping admin email setup"; return 0; fi
    sleep 2
  done
  local token
  token=$(curl -sf -X POST "$kc/realms/master/protocol/openid-connect/token" \
    --data-urlencode "client_id=admin-cli" \
    --data-urlencode "username=${KEYCLOAK_ADMIN_USERNAME}" \
    --data-urlencode "password=${KEYCLOAK_ADMIN_PASSWORD}" \
    --data-urlencode "grant_type=password" 2>/dev/null \
    | grep -o '"access_token":"[^"]*"' | cut -d'"' -f4) || true
  [[ -z "$token" ]] && { log "Could not get Keycloak token — skipping admin email setup"; return 0; }
  local user_id
  user_id=$(curl -sf "$kc/admin/realms/master/users?username=${KEYCLOAK_ADMIN_USERNAME}" \
    -H "Authorization: Bearer $token" 2>/dev/null \
    | grep -o '"id":"[^"]*"' | head -1 | cut -d'"' -f4) || true
  [[ -z "$user_id" ]] && { log "Admin user not found in Keycloak — skipping email setup"; return 0; }
  curl -sf -X PUT "$kc/admin/realms/master/users/$user_id" \
    -H "Authorization: Bearer $token" \
    -H "Content-Type: application/json" \
    -d "{\"email\":\"${ADMIN_EMAIL}\",\"emailVerified\":true}" >/dev/null 2>&1 || true
  ok "Keycloak admin email set to ${ADMIN_EMAIL}"
}

# Load .env
[[ -f "$SCRIPT_DIR/.env" ]] || err ".env not found — create it from .env.example or deploy-prod.sh"
set -a; source "$SCRIPT_DIR/.env"; set +a

# Generate Keycloak realm JSON from template
REALM_TEMPLATE="$SCRIPT_DIR/wac/keycloak/realms/wacchat.json.template"
REALM_OUTPUT="$SCRIPT_DIR/wac/keycloak/realms/wacchat.json"
[[ -f "$REALM_TEMPLATE" ]] || err "Realm template not found: $REALM_TEMPLATE"
_pw_orig="${MAIL_PASSWORD:-}"
if [[ ${#_pw_orig} -eq 16 ]]; then
  MAIL_PASSWORD="${_pw_orig:0:4} ${_pw_orig:4:4} ${_pw_orig:8:4} ${_pw_orig:12:4}"
fi
envsubst '${GOOGLE_CLIENT_ID} ${GOOGLE_CLIENT_SECRET} ${MAIL_USERNAME} ${MAIL_PASSWORD} ${MAIL_FROM}' < "$REALM_TEMPLATE" > "$REALM_OUTPUT"
MAIL_PASSWORD="$_pw_orig"

# Stop existing infra containers
log "Stopping existing infrastructure..."
docker compose \
  -f "$SCRIPT_DIR/docker-compose.yml" \
  -f "$SCRIPT_DIR/docker-compose.local.yml" \
  down 2>/dev/null || true
for name in wacchat-db keycloak-wacchat; do
  docker rm -f "$name" 2>/dev/null || true
done
ok "Cleaned up"

# Start infra with local overrides (KC_HOSTNAME = http://localhost:8180)
log "Starting PostgreSQL + Keycloak (local mode)..."
docker compose \
  -f "$SCRIPT_DIR/docker-compose.yml" \
  -f "$SCRIPT_DIR/docker-compose.local.yml" \
  up -d
ok "Infrastructure up"
set_keycloak_admin_email

echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo " Local dev environment ready"
echo ""
echo " Keycloak : http://localhost:8180"
echo " Database : localhost:5433"
echo ""
echo " Start backend (terminal 1):"
echo "   cd wac/backend && ./mvnw spring-boot:run"
echo "   (direnv loads .env automatically — no extra vars needed)"
echo ""
echo " Start API gateway (terminal 2):"
echo "   cd wac/api-gateway && ./mvnw spring-boot:run"
echo "   (routes to backend:8082 and file-service:8083 by default — no extra vars needed for local dev)"
echo ""
echo " Start frontend (terminal 3):"
echo "   cd wac/frontend && npm start"
echo ""
echo " Then open: http://localhost:4200"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
