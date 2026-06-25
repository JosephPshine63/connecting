# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project layout

All source lives under `wac/`:

```
wac/
├── backend/          # Spring Boot 3.4.1 API (Java 17, Maven)
├── frontend/         # Angular 19 SPA (TypeScript, npm)
├── database/         # Reference schema SQL (schema.sql)
├── keycloak/         # Keycloak realm export (realms/connecting.json)
├── docker-compose-connecting/  # Preferred compose file (Postgres + Keycloak, with volumes)
└── docker-compose/   # Alternate compose (no Postgres volume persistence)
```

## Commands

### Infrastructure (run first)

```bash
cd wac/docker-compose-connecting
docker-compose up -d          # starts PostgreSQL :5432 and Keycloak :9090
docker-compose down           # stop and remove containers
```

### Backend

```bash
cd wac/backend
./mvnw spring-boot:run        # run in dev mode (port 8080)
./mvnw clean package          # build fat JAR
./mvnw test                   # run all tests
./mvnw test -Dtest=ClassName  # run a single test class
```

Swagger UI: `http://localhost:8080/swagger-ui.html`

### Frontend

```bash
cd wac/frontend
npm install
npm start                     # ng serve — dev server at http://localhost:4200
npm run build                 # production build
npm test                      # Karma/Jasmine unit tests
npm run api-gen               # regenerate API client from src/openapi/openapi.json
```

## Architecture

### Backend domain structure

Each domain package (under `dev.pioruocco.connecting`) follows the same layout:

```
<domain>/
  <Entity>.java          # JPA entity
  <Entity>Repository.java
  <Entity>Service.java
  <Entity>Controller.java
  <Entity>Mapper.java    # manual mapping, no MapStruct
  <Entity>Request.java / <Entity>Response.java
```

Domains: `chat`, `message`, `user`, `notification`, `file`, `security`, `ws`, `interceptor`, `common`.

### Key cross-cutting concerns

- **User synchronization** — `UserSynchronizerFilter` runs on every authenticated request and upserts Keycloak JWT claims (sub, email, name) into the local `users` table via `UserSynchronizer`. This is how the app bootstraps users without a separate registration flow.
- **Auth** — Spring OAuth2 Resource Server validates JWTs issued by Keycloak (`http://localhost:9090/realms/connecting`). `KeycloakJwtAuthenticationConverter` extracts realm roles from the `realm_access.roles` claim.
- **WebSocket** — STOMP over SockJS. Endpoint `/ws`, app prefix `/app`, user-destination prefix `/user`. Simple in-memory broker on `/user`. The `@Order(HIGHEST_PRECEDENCE + 99)` on `WebSocketConfig` is intentional to let Spring Security filter handle the WS handshake first.
- **File uploads** — served from `./uploads` (configurable via `application.file.uploads.media-output-path`); max multipart size 50 MB.
- **Flyway** — present in dependencies but `flyway.enabled: false` in `application.yml`; schema is managed via `database/schema.sql` applied manually. JPA `ddl-auto: update` handles incremental DDL in dev.

### Frontend

- Angular services under `src/app/services/` are **fully auto-generated** from `src/openapi/openapi.json` via `ng-openapi-gen`. Do not hand-edit those files; regenerate them with `npm run api-gen` after any backend API change.
- `KeycloakService` (`src/app/utils/keycloak/keycloak.service.ts`) wraps `keycloak-js`; Keycloak realm is `connecting`, client ID is `connecting-app`.
- `KeycloakHttpInterceptor` attaches the Bearer token to every outgoing HTTP request.
- Real-time messaging uses SockJS + STOMP; the connection is established in `MainComponent`.

### Data model

Three tables: `users`, `chat` (one row per pair of users), `messages` (with `state` SENT/SEEN and `type` TEXT/IMAGE/AUDIO). Messages link to a chat via `chat_id`. User IDs are Keycloak `sub` UUIDs (strings), not auto-generated PKs.

## Configuration

`wac/backend/src/main/resources/application.yml` — key values to match your local setup:

| Key | Default |
|-----|---------|
| `spring.datasource.url` | `jdbc:postgresql://localhost:5432/postgres` |
| `spring.datasource.username` | `admin` |
| `spring.datasource.password` | `admin` |
| `spring.security.oauth2.resourceserver.jwt.issuer-uri` | `http://localhost:9090/realms/connecting` |
| `application.file.uploads.media-output-path` | `./uploads` |

The Docker Compose files use `POSTGRES_DB: connecting_db`; the `application.yml` datasource URL points to `postgres` (default DB). Align them if you change either.
