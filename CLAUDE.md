# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project layout

```
.
├── docker-compose.yml          # Base compose (PostgreSQL :5433 + Keycloak :8180 + RabbitMQ :5672/61613/15672)
├── docker-compose.local.yml    # Local override — sets KC_HOSTNAME to http://localhost:8180
├── deploy-local.sh             # Preferred local startup script (sources .env, renders realm template)
├── deploy-prod.sh              # Production deploy script (builds images, pushes if --push)
├── .env.example                # Copy to .env and fill in values
├── docker-compose.observability.yml       # Prometheus + Grafana + Loki + Tempo
├── docker-compose.observability.local.yml # Local override
├── observability/              # Prometheus/Loki/Tempo configs + provisioned Grafana dashboard
└── wac/
    ├── backend/                # Spring Boot 3.4.1 API (Java 17, Maven)
    ├── api-gateway/            # Spring Cloud Gateway — single edge entrypoint (Java 17, Maven)
    ├── file-service/           # Standalone file storage microservice — Cloudflare R2 (Java 17, Maven)
    ├── notification-service/   # Standalone realtime/WebSocket microservice — STOMP over RabbitMQ (Java 17, Maven)
    ├── rabbitmq/                # enabled_plugins (rabbitmq_management, rabbitmq_stomp), mounted into the RabbitMQ container
    ├── frontend/                # Angular 19 SPA (TypeScript, npm)
    ├── database/                # Reference schema SQL (schema.sql)
    ├── keycloak/realms/         # wacchat.json.template — rendered to wacchat.json at deploy time
    └── documentation/           # Additional docs
```

## Commands

### Initial setup

```bash
cp .env.example .env            # fill in passwords, Google OAuth, mail credentials
```

### Infrastructure

```bash
./deploy-local.sh               # starts PostgreSQL :5433, Keycloak :8180, RabbitMQ :5672/61613/15672, and the observability stack (sources .env, renders realm JSON)
docker compose down             # stop containers
```

Do **not** use `docker compose up` directly — `deploy-local.sh` renders `wacchat.json` from the template first, sets the correct `KC_HOSTNAME` override for local development, and also brings up `docker-compose.observability.yml` (Prometheus :9090, Grafana :3000, Loki :3100, Tempo :4318/4317/3200) on the same Docker network.

### First-run database schema

After starting the infrastructure for the first time, apply the schema:

```bash
psql -h localhost -p 5433 -U wacchat -d wacchat_db -f wac/database/schema.sql
```

### Backend

```bash
cd wac/backend
./mvnw spring-boot:run          # dev server at http://localhost:8082 (direnv auto-loads .env)
./mvnw clean package            # build fat JAR
./mvnw test                     # run all tests
./mvnw test -Dtest=ClassName    # run a single test class
```

Swagger UI: `http://localhost:8082/swagger-ui.html`

### File service

```bash
cd wac/file-service
./mvnw spring-boot:run          # dev server at http://localhost:8083
```

### Notification service

```bash
cd wac/notification-service
./mvnw spring-boot:run          # dev server at http://localhost:8084 — connects to RabbitMQ (localhost:5672 AMQP, :61613 STOMP) and backend:8082
```

### API Gateway

```bash
cd wac/api-gateway
./mvnw spring-boot:run          # dev server at http://localhost:8081 — routes to backend:8082, file-service:8083, notification-service:8084
```

The frontend never calls backend, file-service, or notification-service directly — it always goes through the gateway (`proxy.conf.json` in dev, `nginx.conf` in prod).

### Frontend

```bash
cd wac/frontend
npm install
npm start                       # ng serve — dev server at http://localhost:4200
npm run build                   # production build
npm test                        # Karma/Jasmine unit tests
npm test -- --include='**/foo.component.spec.ts'   # run a single test file
```

To regenerate the Angular API client after a backend API change (backend must be running):

```bash
curl http://localhost:8082/v3/api-docs -o wac/frontend/src/openapi/openapi.json
cd wac/frontend && npm run api-gen
```

## Architecture

### API Gateway

`wac/api-gateway` (Spring Cloud Gateway, WebFlux/Netty — reactive, not the servlet stack the other services use) is the single edge entrypoint the frontend talks to. Routes (YAML-driven, `wac/api-gateway/src/main/resources/application.yml`):

| Predicate | Target |
|-----------|--------|
| `/api/**` | `wac/backend` |
| `/ws/**` | `wac/notification-service` (WebSocket upgrade, proxied transparently) |
| `/files/**` | `wac/file-service`, rewritten to `/api/v1/files/**` |

CORS is centralized at the gateway via `spring.cloud.gateway.globalcors` (allowed origins `http://localhost:4200` and `https://wacchat.win`); the backend no longer sets `Access-Control-Allow-*` headers itself. The one exception is notification-service's own `WebSocketConfig` SockJS origin allowlist (`registry.addEndpoint("/ws").setAllowedOrigins(...)`), which is a separate, independent handshake-level check kept as defense-in-depth — the gateway forwards the browser's `Origin` header unmodified on `/ws/**`. The gateway does not inject the file-service/backend internal API keys; each service's own `InternalAuthFilter` still gates its internal endpoints regardless of path.

### Realtime notification service

`wac/notification-service` is a standalone module extracted from the backend that owns the entire STOMP/WebSocket stack — the backend has no `/ws` endpoint anymore. It uses `enableStompBrokerRelay` (not the in-memory `SimpleBroker`) pointed at RabbitMQ's STOMP plugin (port 61613, `rabbitmq_stomp`), so subscription state lives in the broker instead of per-instance JVM memory and multiple notification-service instances can share WebSocket push delivery without missing messages.

Two independent RabbitMQ channels are involved:
- **STOMP (61613)** — the broker relay itself, used by Spring's WS layer for the actual client subscriptions/fan-out (`/topic`, `/queue` prefixes; `/user/**` destinations are translated internally to per-session `/queue/...` names before being relayed).
- **AMQP (5672)** — a separate application-level event bus. Backend's `NotificationService.sendNotification(userId, notification)` no longer calls `SimpMessagingTemplate` directly (it can't — the WS layer lives in another process); instead it publishes a `NotificationEvent(userId, notification)` to the `wacchat.notifications` exchange (routing key `notification`, queue `wacchat.notifications.queue` — names configurable under `application.notification.*`, identical in both modules). notification-service's `NotificationListener` (`@RabbitListener`) consumes it and calls `convertAndSendToUser(userId, "/chat", notification)`.

`Notification`, `NotificationType`, `NotificationEvent`, and `MessageType` are duplicated verbatim (identical package + class name) in both `wac/backend` and `wac/notification-service` — there's no shared library between independently-deployable services (see `KeycloakJwtAuthenticationConverter`, duplicated the same way), and matching FQCNs let `Jackson2JsonMessageConverter`'s default `__TypeId__` header resolve to the same class on both ends without extra `DefaultClassMapper` config.

The single-session lock (`AuthChannelInterceptor`, moved into notification-service) can no longer read `SessionGuard`/`UserRepository` directly (that's backend-only DB logic), so on STOMP `CONNECT` it makes a synchronous call via `SessionValidationClient` (WebClient + Resilience4j `sessionValidation` circuit breaker/retry instance) to backend's internal `POST /api/v1/internal/sessions/validate` (guarded by `InternalAuthFilter`, shared-secret header `X-Internal-Api-Key` / `BACKEND_INTERNAL_API_KEY`). This call **fails open** (treats backend-down as "not conflicting") — the session lock is a UX nicety, not a security boundary, and a lost WS connection is worse than a rare double-session.

### Observability

Backend, api-gateway, file-service, and notification-service all export traces via OpenTelemetry/Micrometer Tracing (OTLP HTTP) to Tempo, logs via a direct Logback→Loki appender (`logback-spring.xml` in each service, correlated by trace/span id), and metrics via Micrometer/Actuator scraped by Prometheus. Grafana (`http://localhost:3000`, admin/admin unless overridden) has a provisioned dashboard (`observability/grafana/dashboards/wacchat-overview.json`: request rate, p95 latency, error rate, JVM heap per service) plus Prometheus/Loki/Tempo datasources auto-provisioned. `WebClientConfig` in the backend builds `FileServiceClient`'s `WebClient` off the autoconfigured `WebClient.Builder` specifically so trace context propagates from backend → file-service calls (same pattern in notification-service's `WebClientConfig`/`SessionValidationClient` for calls to backend). Locally, services (running on the host) reach Tempo/Loki via `localhost`; `deploy-prod.sh` overrides `OTLP_TRACING_ENDPOINT`/`LOKI_URL` to container DNS names since the services run in Docker there.

### Backend domain structure

Package root: `dev.pioruocco.wacchat`. Each domain follows:

```
<domain>/
  <Entity>.java
  <Entity>Repository.java
  <Entity>Service.java
  <Entity>Controller.java
  <Entity>Mapper.java        # manual mapping — no MapStruct
  <Entity>Request.java / <Entity>Response.java
```

Domains: `chat`, `message`, `user`, `notification`, `file`, `security`, `interceptor`, `common`. (The `ws` domain — WebSocket/STOMP config and `AuthChannelInterceptor` — moved out to `wac/notification-service`; see Architecture.)

All JPA entities extend `common/BaseAuditingEntity`, which auto-populates `createdDate` and `lastModifiedDate` via Spring Data JPA auditing. `chat` IDs are UUID strings; `messages` uses `msg_seq` (a PostgreSQL sequence starting at 1).

### Key cross-cutting concerns

- **User synchronization** — `UserSynchronizerFilter` runs on every authenticated request and upserts Keycloak JWT claims (`sub`, `email`, `name`) into the local `users` table via `UserSynchronizer`. No separate registration flow.
- **Auth** — Spring OAuth2 Resource Server validates JWTs from Keycloak. `KeycloakJwtAuthenticationConverter` extracts realm roles from `realm_access.roles`.
- **WebSocket** — STOMP over SockJS, entirely in `wac/notification-service` (see Architecture). Endpoint `/ws`, app prefix `/app`, user-destination prefix `/user`. STOMP broker relay to RabbitMQ (not an in-memory broker). `@Order(HIGHEST_PRECEDENCE + 99)` on `WebSocketConfig` lets Spring Security handle the WS handshake before STOMP processing. `AuthChannelInterceptor` validates the Bearer JWT on every STOMP `CONNECT` frame (via the JwtDecoder/KeycloakJwtAuthenticationConverter beans, not the HTTP filter chain — see `SecurityConfig`), calls backend for the single-session-lock check, and enforces that a user can only subscribe to their own `/user/{userId}/chat` destination.

  | Direction | Destination |
  |-----------|-------------|
  | Send message | `/app/chat` |
  | Receive notifications | `/user/{userId}/chat` |
- **File uploads** — both message media (images, under `messages/{userId}/...`) and user avatars (under `avatars/{userId}/...`) are stored in a public-read Cloudflare R2 bucket by `wac/file-service` (standalone module, `R2StorageService`, AWS SDK v2 S3-compatible client — see its own `R2_*` env vars). Backend's `file` domain now only holds the client side: `FileServiceClient` (`WebClient`, guarded by Resilience4j circuit breaker + retry, config under `application.file-service.*` / `resilience4j.*.instances.fileService`) and `FileUtils`. Max multipart size 50 MB. `Message.mediaFilePath` holds a public R2 URL; `MessageMapper`/`Notification` resolve it via `FileUtils.resolveMedia`, which also still reads pre-migration messages whose `mediaFilePath` is a legacy local disk path (returned as base64) for backward compatibility.
- **Flyway** — present in deps but `flyway.enabled: false`; schema is applied manually from `database/schema.sql`. JPA `ddl-auto: update` handles incremental DDL in dev.
- **Scheduled cleanup** — `UserCleanupService` runs every Monday at 03:00 AM; deletes inactive users (>21 days, configurable) from both Keycloak and the local DB. The `ADMIN_EMAIL` / `application.cleanup.protected-email` account is never deleted.
- **Mail** — Resend SMTP (`smtp.resend.com:465`). Credentials via `MAIL_USERNAME` / `MAIL_PASSWORD` env vars.
- **Single-session lock** — `SessionGuard` (`user` domain) compares the JWT `sid` claim against `User.activeSessionId` on every request, inside `UserSynchronizer.synchronizeWithIdp()` (called from `UserSynchronizerFilter`). A conflicting session is only rejected while the holder is "fresh" (`lastSeen` within `application.session.stale-after-seconds`, default 120s, refreshed by a frontend heartbeat every 60s); past that window the new session takes over. Conflicts throw `SessionConflictException` → HTTP 409 `SESSION_CONFLICT`. Explicit logout clears the lock via a dedicated endpoint on `UserController`. `SessionValidationController` (`/api/v1/internal/sessions/validate`, gated by `InternalAuthFilter`) exposes the same `SessionGuard` check over REST for notification-service's STOMP `CONNECT` handler, which can no longer read the DB in-process.

### Frontend

- **Single-route SPA** — `app.routes.ts` defines one route (`''` → `MainComponent`). `pages/main` owns the STOMP connection and top-level layout; sub-components are `components/chat-list`, `components/username-setup`, `components/avatar-upload`, `components/user-card`, and `components/session-blocked`.
- Services under `src/app/services/` are **fully auto-generated** from `src/openapi/openapi.json` via `ng-openapi-gen`. Never hand-edit; run `npm run api-gen` after any backend API change. Exception: `utils/username/username.service.ts` is hand-written and calls `/api/v1/users/me`, `/api/v1/users/username`, and `/api/v1/users/check-username` directly — it is not generated. Avatar upload (`components/avatar-upload`) and the contact profile view (`components/user-card`) use the generated `UserService` instead.
- `components/username-setup` is a modal shown on first login when the user has no username. It calls `UsernameService` to validate uniqueness in real time and to set the username before granting access to the main chat UI.
- **Session lock UI** — `SessionGuardService` (`utils/session/`) holds a `blocked` signal, flipped by `KeycloakHttpInterceptor` when it sees an HTTP 409 with `error.code === 'SESSION_CONFLICT'` (no WebSocket involved). `components/session-blocked` renders a blocking overlay while `blocked()` is true, offering Retry (re-checks `/api/v1/users/me`) or Logout.
- **Desktop notifications** — `utils/notifications/browser-notification.service.ts` wraps the native `Notification` Web API; `MainComponent` requests permission on init and fires a notification (with click-to-open-chat) when a message/image arrives for a chat that isn't currently open/focused.
- `KeycloakService` (`src/app/utils/keycloak/keycloak.service.ts`) wraps `keycloak-js`; Keycloak URL is read from `environment.keycloakUrl` (set per environment file — not hardcoded in the service). Realm and client ID (`wacchat` / `wacchat-app`) are set there.
- `KeycloakHttpInterceptor` (`src/app/utils/http/`) attaches the Bearer token to every outgoing HTTP request.
- Real-time messaging via SockJS + STOMP; connection established in `MainComponent`. Incoming WebSocket frames are typed as `Notification` objects (backend `notification/Notification.java`) with a `NotificationType` discriminator.
- UI stack: Bootstrap 5, Font Awesome 6, Quill (rich-text editor), `@ctrl/ngx-emoji-mart`.
- Environments: `src/environments/environment.ts` (dev) and `environment.prod.ts` (prod) set `keycloakUrl`, `appUrl`, and `apiRootUrl`.

### Data model

Three tables: `users`, `chat` (one row per user pair), `messages` (`state`: SENT/SEEN; `type`: TEXT/IMAGE/AUDIO). User IDs are Keycloak `sub` UUIDs (strings), not auto-generated PKs. `users` also stores `username` (unique, 3–20 chars, pattern `^[a-z0-9_-]+$`), `last_seen` (timestamp), and `avatar_url` (public R2 object URL); all three are nullable for users who haven't completed onboarding / set a photo.

## Configuration

`wac/backend/src/main/resources/application.yml` — env vars override defaults:

| Env var | Default |
|---------|---------|
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://localhost:5433/wacchat_db` |
| `SPRING_DATASOURCE_USERNAME` | `admin` |
| `SPRING_DATASOURCE_PASSWORD` | `admin` |
| `KEYCLOAK_ISSUER_URI` | `http://localhost:8180/realms/wacchat` |
| `KEYCLOAK_ADMIN_URL` | `http://keycloak-wacchat:8080` (`.envrc` overrides to `http://localhost:8180` for local dev) |
| `KEYCLOAK_ADMIN_USERNAME` / `KEYCLOAK_ADMIN_PASSWORD` | `admin` / `admin` |
| `MAIL_USERNAME` / `MAIL_PASSWORD` / `MAIL_FROM` | (empty — mail disabled) |
| `ADMIN_EMAIL` | (empty — cleanup protects no account) |
| `FILE_SERVICE_BASE_URL` | `http://localhost:8083` |
| `FILE_SERVICE_INTERNAL_API_KEY` | (empty) |
| `RABBITMQ_HOST` / `RABBITMQ_PORT` | `localhost` / `5672` |
| `RABBITMQ_USER` / `RABBITMQ_PASSWORD` | `wacchat` / `wacchat` |
| `BACKEND_INTERNAL_API_KEY` | (empty — internal session-validation endpoint rejects all calls until set) |
| `OTLP_TRACING_ENDPOINT` | `http://localhost:4318/v1/traces` |
| `LOKI_URL` | `http://localhost:3100/loki/api/v1/push` |
| `TRACING_SAMPLING_PROBABILITY` | `1.0` |

`OTLP_TRACING_ENDPOINT`, `LOKI_URL`, and `TRACING_SAMPLING_PROBABILITY` are shared by backend, api-gateway, file-service, and notification-service (same env vars, same defaults, in each module's `application.yml`). `RABBITMQ_*` are shared by backend and notification-service.

`wac/file-service/src/main/resources/application.yml` — `R2_ACCOUNT_ID` / `R2_ACCESS_KEY_ID` / `R2_SECRET_ACCESS_KEY` / `R2_BUCKET_NAME` / `R2_PUBLIC_BASE_URL` (empty — avatar/media upload disabled) and `FILE_SERVICE_INTERNAL_API_KEY` (must match the value backend uses to call it).

`wac/notification-service/src/main/resources/application.yml` — `RABBITMQ_HOST`/`RABBITMQ_PORT`/`RABBITMQ_USER`/`RABBITMQ_PASSWORD` (same defaults as backend), `RABBITMQ_STOMP_PORT` (default `61613`, the broker-relay port — distinct from the AMQP port), `KEYCLOAK_ISSUER_URI` (same default as backend, needed for its own `JwtDecoder`), `BACKEND_BASE_URL` (default `http://localhost:8082`) and `BACKEND_INTERNAL_API_KEY` (must match backend's value) for the session-validation call.

`wac/api-gateway/src/main/resources/application.yml` — `BACKEND_BASE_URL` (default `http://localhost:8082`), `FILE_SERVICE_BASE_URL` (default `http://localhost:8083`), and `NOTIFICATION_SERVICE_BASE_URL` (default `http://localhost:8084`).

`wac/keycloak/realms/wacchat.json` is **generated** from `wacchat.json.template` by `deploy-local.sh` and `deploy-prod.sh` via `envsubst`. Never commit the rendered `.json` file; edit the `.json.template` instead.

`wac/keycloak/themes/wacchat/` is a custom Keycloak theme (`loginTheme`/`accountTheme: wacchat` in the realm template) — FreeMarker templates (`login.ftl`, `register.ftl`, `login-reset-password.ftl`, `login-update-password.ftl`, etc.) under `login/` give the login/register/password-reset flows the WacChat dark glassmorphism look instead of Keycloak's default theme. Note: the "forgot password" flow asks for the Keycloak login identifier (email, or username for Google-linked accounts), which is distinct from the in-app chat `username` stored in the `users` table.

CORS is enforced at `wac/api-gateway` (`spring.cloud.gateway.globalcors`), not in the backend. Allowed origins everywhere (gateway CORS + `WebSocketConfig`'s SockJS handshake check): `http://localhost:4200` and `https://wacchat.win`.
