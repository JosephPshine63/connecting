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

# Load .env
[[ -f "$SCRIPT_DIR/.env" ]] || err ".env not found — create it from .env.example or deploy-prod.sh"
set -a; source "$SCRIPT_DIR/.env"; set +a

# Generate Keycloak realm JSON from template
REALM_TEMPLATE="$SCRIPT_DIR/wac/keycloak/realms/connecting.json.template"
REALM_OUTPUT="$SCRIPT_DIR/wac/keycloak/realms/connecting.json"
[[ -f "$REALM_TEMPLATE" ]] || err "Realm template not found: $REALM_TEMPLATE"
GOOGLE_CLIENT_ID="${GOOGLE_CLIENT_ID:-}" GOOGLE_CLIENT_SECRET="${GOOGLE_CLIENT_SECRET:-}"
envsubst '${GOOGLE_CLIENT_ID} ${GOOGLE_CLIENT_SECRET}' < "$REALM_TEMPLATE" > "$REALM_OUTPUT"

# Stop existing infra containers
log "Stopping existing infrastructure..."
docker compose \
  -f "$SCRIPT_DIR/docker-compose.yml" \
  -f "$SCRIPT_DIR/docker-compose.local.yml" \
  down 2>/dev/null || true
for name in connecting-db keycloak-connecting; do
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

echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo " Local dev environment ready"
echo ""
echo " Keycloak : http://localhost:8180"
echo " Database : localhost:5433"
echo ""
echo " Start backend (terminal 1):"
echo "   cd wac/backend"
echo "   source ../../.env"
echo "   SPRING_DATASOURCE_USERNAME=\$POSTGRES_USER SPRING_DATASOURCE_PASSWORD=\$POSTGRES_PASSWORD MAIL_USERNAME=\$MAIL_USERNAME MAIL_PASSWORD=\$MAIL_PASSWORD ./mvnw spring-boot:run"
echo ""
echo " Start frontend (terminal 2):"
echo "   cd wac/frontend && npm start"
echo ""
echo " Then open: http://localhost:4200"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
