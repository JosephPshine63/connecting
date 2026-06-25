#!/usr/bin/env bash
set -euo pipefail

# ─── Configurable variables ──────────────────────────────────────────────────
REGISTRY=""                          # e.g. "registry.example.com/myorg" (leave empty for no prefix)
IMAGE_BACKEND="connecting-backend"
IMAGE_FRONTEND="connecting-frontend"
TAG="latest"
CONTAINER_BACKEND="connecting-backend"
CONTAINER_FRONTEND="connecting-frontend"
PORT_BACKEND=8080
PORT_FRONTEND=4200
COMPOSE_DIR="wac/docker-compose-connecting"
# ─────────────────────────────────────────────────────────────────────────────

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BACKEND_DIR="$SCRIPT_DIR/wac/backend"
FRONTEND_DIR="$SCRIPT_DIR/wac/frontend"

NO_CACHE=false
PUSH=false
ENV="development"

# ─── CLI flags ───────────────────────────────────────────────────────────────
for arg in "$@"; do
  case $arg in
    --no-cache)    NO_CACHE=true ;;
    --push)        PUSH=true ;;
    --env=*)       ENV="${arg#*=}" ;;
    -h|--help)
      echo "Usage: ./deploy.sh [--no-cache] [--push] [--env=development|staging|production]"
      exit 0
      ;;
    *)
      echo "[ERROR] Unknown argument: $arg"
      echo "Usage: ./deploy.sh [--no-cache] [--push] [--env=development|staging|production]"
      exit 1
      ;;
  esac
done

# ─── Helpers ─────────────────────────────────────────────────────────────────
log()  { echo "[$(date '+%H:%M:%S')] $*"; }
ok()   { echo "[$(date '+%H:%M:%S')] ✓ $*"; }
err()  { echo "[$(date '+%H:%M:%S')] ✗ $*" >&2; exit 1; }

# ─── Preflight checks ────────────────────────────────────────────────────────
log "=== Connecting deploy — env=$ENV ==="

[[ -f "$SCRIPT_DIR/deploy.sh" ]] || err "Must be run from the repo root (deploy.sh not found at $SCRIPT_DIR)"

command -v docker  >/dev/null 2>&1 || err "Docker is not installed or not in PATH"
docker info        >/dev/null 2>&1 || err "Docker daemon is not running"
command -v docker compose >/dev/null 2>&1 || \
  command -v docker-compose >/dev/null 2>&1 || \
  err "docker compose (v2) or docker-compose (v1) is required"

[[ -d "$BACKEND_DIR" ]]  || err "Backend directory not found: $BACKEND_DIR"
[[ -d "$FRONTEND_DIR" ]] || err "Frontend directory not found: $FRONTEND_DIR"

ok "Preflight checks passed"

# ─── Environment-specific overrides ──────────────────────────────────────────
case "$ENV" in
  production)
    TAG="stable"
    log "Production mode: tag=$TAG, push enforced"
    PUSH=true
    ;;
  staging)
    TAG="staging"
    log "Staging mode: tag=$TAG"
    ;;
  development)
    log "Development mode: tag=$TAG"
    ;;
  *)
    err "Unknown --env value '$ENV'. Use: development, staging, production"
    ;;
esac

# Compose the full image names (with optional registry prefix)
if [[ -n "$REGISTRY" ]]; then
  FULL_BACKEND="$REGISTRY/$IMAGE_BACKEND:$TAG"
  FULL_FRONTEND="$REGISTRY/$IMAGE_FRONTEND:$TAG"
else
  FULL_BACKEND="$IMAGE_BACKEND:$TAG"
  FULL_FRONTEND="$IMAGE_FRONTEND:$TAG"
fi

BUILD_FLAGS=""
if $NO_CACHE; then
  BUILD_FLAGS="--no-cache"
  log "Cache disabled (--no-cache)"
fi

# ─── 1. Git pull ─────────────────────────────────────────────────────────────
log "Pulling latest changes from origin..."
command -v git >/dev/null 2>&1 || err "git is not installed or not in PATH"
git -C "$SCRIPT_DIR" pull --ff-only origin main
ok "Repository is up to date"

# ─── 2. Infrastructure (Postgres + Keycloak) ─────────────────────────────────
log "Stopping infrastructure containers for rebuild..."
COMPOSE_CMD="docker compose"
command -v docker compose >/dev/null 2>&1 || COMPOSE_CMD="docker-compose"

(cd "$SCRIPT_DIR/$COMPOSE_DIR" && $COMPOSE_CMD down)
ok "Infrastructure stopped"

# Release any containers still holding the required ports
for port in 5432 9090; do
  conflicting=$(docker ps --format '{{.ID}} {{.Ports}}' | grep ":${port}->" | awk '{print $1}')
  if [[ -n "$conflicting" ]]; then
    log "Port $port still held by container $conflicting — stopping it..."
    docker stop "$conflicting"
  fi
done

log "Starting infrastructure (PostgreSQL + Keycloak)..."
(cd "$SCRIPT_DIR/$COMPOSE_DIR" && $COMPOSE_CMD up -d)
ok "Infrastructure is up"

# ─── 4. Build backend image ──────────────────────────────────────────────────
log "Building backend image: $FULL_BACKEND ..."
docker build $BUILD_FLAGS \
  -t "$FULL_BACKEND" \
  -f "$BACKEND_DIR/Dockerfile" \
  "$BACKEND_DIR"
ok "Backend image built: $FULL_BACKEND"

# ─── 3. Build frontend image ─────────────────────────────────────────────────
log "Building frontend image: $FULL_FRONTEND ..."
docker build $BUILD_FLAGS \
  -t "$FULL_FRONTEND" \
  -f "$FRONTEND_DIR/Dockerfile" \
  "$FRONTEND_DIR"
ok "Frontend image built: $FULL_FRONTEND"

# ─── 4. Push to registry (optional) ─────────────────────────────────────────
if $PUSH; then
  [[ -n "$REGISTRY" ]] || err "--push requires REGISTRY to be set in deploy.sh"
  log "Pushing images to $REGISTRY ..."
  docker push "$FULL_BACKEND"
  docker push "$FULL_FRONTEND"
  ok "Images pushed to registry"
fi

# ─── 5. Stop and remove existing app containers ──────────────────────────────
for name in "$CONTAINER_BACKEND" "$CONTAINER_FRONTEND"; do
  if docker ps -a --format '{{.Names}}' | grep -qx "$name"; then
    log "Stopping and removing container: $name"
    docker stop "$name" 2>/dev/null || true
    docker rm   "$name" 2>/dev/null || true
  fi
done
ok "Old containers removed"

# ─── 6. Run backend container ────────────────────────────────────────────────
log "Starting backend container on port $PORT_BACKEND ..."
docker run -d \
  --name "$CONTAINER_BACKEND" \
  --network "${COMPOSE_DIR##*/}_connecting" \
  -p "$PORT_BACKEND:8080" \
  -e SPRING_DATASOURCE_URL="jdbc:postgresql://connecting-db:5432/connecting_db" \
  -e SPRING_DATASOURCE_USERNAME="admin" \
  -e SPRING_DATASOURCE_PASSWORD="admin" \
  -e SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_ISSUER_URI="http://keycloak-connecting:8080/realms/connecting" \
  -v "$SCRIPT_DIR/wac/backend/uploads:/app/uploads" \
  --restart unless-stopped \
  "$FULL_BACKEND"
ok "Backend container started"

# ─── 7. Run frontend container ───────────────────────────────────────────────
log "Starting frontend container on port $PORT_FRONTEND ..."
docker run -d \
  --name "$CONTAINER_FRONTEND" \
  --network "${COMPOSE_DIR##*/}_connecting" \
  -p "$PORT_FRONTEND:80" \
  --restart unless-stopped \
  "$FULL_FRONTEND"
ok "Frontend container started"

# ─── 8. Health check ─────────────────────────────────────────────────────────
log "Waiting for backend to be ready..."
HEALTH_URL="http://localhost:$PORT_BACKEND/actuator/health"
MAX_RETRIES=30
RETRY_INTERVAL=3

healthy=false
for i in $(seq 1 $MAX_RETRIES); do
  http_code=$(curl -s -o /dev/null -w "%{http_code}" "$HEALTH_URL" 2>/dev/null || echo "000")
  if [[ "$http_code" == "200" ]]; then
    healthy=true
    break
  fi
  # Fallback: if actuator is not enabled, check the base API
  if [[ "$i" -eq 5 ]]; then
    HEALTH_URL="http://localhost:$PORT_BACKEND/swagger-ui.html"
  fi
  echo "  Attempt $i/$MAX_RETRIES — HTTP $http_code (retrying in ${RETRY_INTERVAL}s...)"
  sleep $RETRY_INTERVAL
done

if $healthy; then
  ok "Backend is healthy at $HEALTH_URL"
else
  echo ""
  log "Health check timed out after $((MAX_RETRIES * RETRY_INTERVAL))s — check container logs:"
  echo "  docker logs $CONTAINER_BACKEND"
fi

# ─── Summary ─────────────────────────────────────────────────────────────────
echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo " Deploy complete — env=$ENV"
echo ""
echo " Backend  : http://localhost:$PORT_BACKEND"
echo " Frontend : http://localhost:$PORT_FRONTEND"
echo " Swagger  : http://localhost:$PORT_BACKEND/swagger-ui.html"
echo " Keycloak : http://localhost:9090"
echo ""
echo " Logs:"
echo "   docker logs -f $CONTAINER_BACKEND"
echo "   docker logs -f $CONTAINER_FRONTEND"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
