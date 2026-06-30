# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project layout

```
.
├── docker-compose.yml          # Base compose (PostgreSQL :5433 + Keycloak :8180)
├── docker-compose.local.yml    # Local override — sets KC_HOSTNAME to http://localhost:8180
├── deploy-local.sh             # Preferred local startup script (sources .env, renders realm template)
├── deploy-prod.sh              # Production deploy script (builds images, pushes if --push)
├── .env.example                # Copy to .env and fill in values
└── wac/
    ├── backend/                # Spring Boot 3.4.1 API (Java 17, Maven)
    ├── frontend/               # Angular 19 SPA (TypeScript, npm)
    ├── database/               # Reference schema SQL (schema.sql)
    ├── keycloak/realms/        # wacchat.json.template — rendered to wacchat.json at deploy time
    └── documentation/          # Additional docs
```

## Commands

### Initial setup

```bash
cp .env.example .env            # fill in passwords, Google OAuth, mail credentials
```

### Infrastructure

```bash
./deploy-local.sh               # starts PostgreSQL :5433 and Keycloak :8180 (sources .env, renders realm JSON)
docker compose down             # stop containers
```

Do **not** use `docker compose up` directly — `deploy-local.sh` renders `wacchat.json` from the template first and sets the correct `KC_HOSTNAME` override for local development.

### Backend

```bash
cd wac/backend
./mvnw spring-boot:run          # dev server at http://localhost:8080 (direnv auto-loads .env)
./mvnw clean package            # build fat JAR
./mvnw test                     # run all tests
./mvnw test -Dtest=ClassName    # run a single test class
```

Swagger UI: `http://localhost:8080/swagger-ui.html`

### Frontend

```bash
cd wac/frontend
npm install
npm start                       # ng serve — dev server at http://localhost:4200
npm run build                   # production build
npm test                        # Karma/Jasmine unit tests
npm run api-gen                 # regenerate API client from src/openapi/openapi.json
```

## Architecture

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

Domains: `chat`, `message`, `user`, `notification`, `file`, `security`, `ws`, `interceptor`, `common`.

### Key cross-cutting concerns

- **User synchronization** — `UserSynchronizerFilter` runs on every authenticated request and upserts Keycloak JWT claims (`sub`, `email`, `name`) into the local `users` table via `UserSynchronizer`. No separate registration flow.
- **Auth** — Spring OAuth2 Resource Server validates JWTs from Keycloak. `KeycloakJwtAuthenticationConverter` extracts realm roles from `realm_access.roles`.
- **WebSocket** — STOMP over SockJS. Endpoint `/ws`, app prefix `/app`, user-destination prefix `/user`. In-memory broker on `/user`. `@Order(HIGHEST_PRECEDENCE + 99)` on `WebSocketConfig` lets Spring Security handle the WS handshake before STOMP processing.
- **File uploads** — stored at `./uploads` (env: `application.file.uploads.media-output-path`); max multipart size 50 MB.
- **Flyway** — present in deps but `flyway.enabled: false`; schema is applied manually from `database/schema.sql`. JPA `ddl-auto: update` handles incremental DDL in dev.
- **Scheduled cleanup** — `UserCleanupService` runs every Monday at 03:00 AM; deletes inactive users (>21 days, configurable) from both Keycloak and the local DB. The `ADMIN_EMAIL` / `application.cleanup.protected-email` account is never deleted.
- **Mail** — Resend SMTP (`smtp.resend.com:465`). Credentials via `MAIL_USERNAME` / `MAIL_PASSWORD` env vars.

### Frontend

- Services under `src/app/services/` are **fully auto-generated** from `src/openapi/openapi.json` via `ng-openapi-gen`. Never hand-edit; run `npm run api-gen` after any backend API change.
- `KeycloakService` (`src/app/utils/keycloak/keycloak.service.ts`) wraps `keycloak-js`; reads `environment.keycloakUrl` (dev: `http://localhost:8180`).
- `KeycloakHttpInterceptor` attaches Bearer token to every outgoing HTTP request.
- Real-time messaging via SockJS + STOMP; connection established in `MainComponent`.
- Environments: `src/environments/environment.ts` (dev) and `environment.prod.ts` (prod) set `keycloakUrl`, `appUrl`, and `apiRootUrl`.

### Data model

Three tables: `users`, `chat` (one row per user pair), `messages` (`state`: SENT/SEEN; `type`: TEXT/IMAGE/AUDIO). User IDs are Keycloak `sub` UUIDs (strings), not auto-generated PKs.

## Configuration

`wac/backend/src/main/resources/application.yml` — env vars override defaults:

| Env var | Default |
|---------|---------|
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://localhost:5433/wacchat_db` |
| `SPRING_DATASOURCE_USERNAME` | `admin` |
| `SPRING_DATASOURCE_PASSWORD` | `admin` |
| `KEYCLOAK_ISSUER_URI` | `http://localhost:8180/realms/wacchat` |
| `KEYCLOAK_ADMIN_URL` | `http://keycloak-wacchat:8080` |
| `MAIL_USERNAME` / `MAIL_PASSWORD` / `MAIL_FROM` | (empty — mail disabled) |
| `ADMIN_EMAIL` | (empty — cleanup protects no account) |

`wac/keycloak/realms/wacchat.json` is **generated** from `wacchat.json.template` by `deploy-local.sh` and `deploy-prod.sh` via `envsubst`. Never commit the rendered `.json` file; edit the `.json.template` instead.

CORS and WebSocket allowed origins: `http://localhost:4200` and `https://wacchat.win`.
