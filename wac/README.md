# Connecting

**Full-stack real-time chat application** built with Spring Boot, Angular, PostgreSQL, and Keycloak. Users authenticate via OAuth2 (Keycloak), exchange messages over WebSocket (STOMP/SockJS), and upload media files through a REST API.

---

## Features

- Real-time messaging via STOMP/SockJS WebSocket
- OAuth2/OpenID Connect authentication delegated entirely to Keycloak
- Automatic user provisioning: first authenticated request upserts Keycloak JWT claims into local DB
- Per-conversation chat threads with message state tracking (SENT → SEEN)
- Media file upload/download (text, image, audio — up to 50 MB per file)
- REST API documented with OpenAPI/Swagger at `/swagger-ui.html`
- Angular client fully generated from the OpenAPI spec (`ng-openapi-gen`)

---

## Tech stack

| Layer | Technology |
|-------|-----------|
| Backend | Java 17, Spring Boot 3.4.1, Spring Data JPA, Spring WebSocket, Spring Security OAuth2 Resource Server |
| Frontend | Angular 19, TypeScript 5.6, Keycloak-js 26, SockJS + STOMP, Bootstrap 5 |
| Database | PostgreSQL (latest), managed schema via `database/schema.sql` |
| Auth | Keycloak 26.0.0 (realm `connecting`, client `connecting-app`) |
| Build | Maven 3 (backend), Angular CLI 19 / npm (frontend) |
| Dev infra | Docker Compose |

---

## Prerequisites

- Java 17+
- Node.js 20+ and npm
- Docker and Docker Compose
- Maven 3.8+ (or use the included `mvnw` wrapper)

---

## Installation

### 1. Start infrastructure

```bash
cd wac/docker-compose-connecting
docker-compose up -d
```

This brings up:
- PostgreSQL on port `5433` (mapped from container `5432`)
- Keycloak on port `8180` (admin console at `http://localhost:8180/admin`), with the `connecting` realm auto-imported from `keycloak/realms/connecting.json`

### 2. Apply database schema

```bash
psql -h localhost -U admin -d connecting_db -f wac/database/schema.sql
```

> Flyway is present as a dependency but disabled. JPA `ddl-auto: update` handles schema drift in dev; apply `schema.sql` on a fresh database.

### 3. Start the backend

```bash
cd wac/backend
./mvnw spring-boot:run
```

API available at `http://localhost:8080`. Swagger UI at `http://localhost:8080/swagger-ui.html`.

### 4. Start the frontend

```bash
cd wac/frontend
npm install
npm start
```

App available at `http://localhost:4200`.

---

## Configuration

### Backend — `wac/backend/src/main/resources/application.yml`

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/postgres   # change DB name if needed
    username: admin
    password: admin
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: http://localhost:8180/realms/connecting
  servlet:
    multipart:
      max-file-size: 50MB

application:
  file:
    uploads:
      media-output-path: ./uploads   # local path where media files are stored
```

> The Docker Compose default database is `connecting_db`, but `application.yml` points to `postgres`. Either update the datasource URL or adjust `POSTGRES_DB` in the compose file to match.

### Frontend — `wac/frontend/src/app/utils/keycloak/keycloak.service.ts`

Keycloak config is hardcoded in the service:

```typescript
new Keycloak({
  url: 'http://localhost:8180',
  realm: 'connecting',
  clientId: 'connecting-app'
});
```

Change these values if you rename the realm or client in Keycloak.

---

## Usage

### Regenerate the Angular API client

After any backend API change, regenerate the Angular services from the updated OpenAPI spec:

```bash
# Export the spec (backend must be running)
curl http://localhost:8080/v3/api-docs -o wac/frontend/src/openapi/openapi.json

cd wac/frontend
npm run api-gen
```

### WebSocket destinations

| Direction | Destination pattern |
|-----------|-------------------|
| Send message | `/app/chat` |
| Receive notifications | `/user/{userId}/chat` |

---

## User management — invite-only access

Self-registration is **disabled** in the `connecting` realm (`registrationAllowed: false`). Only the admin can create accounts.

### Inviting a new user

1. Open the Keycloak admin console:
   - **Production:** `https://auth.wacchat.win/admin`
   - **Dev:** `http://localhost:8180/admin`

2. Log in and select the **connecting** realm from the top-left dropdown.

3. Go to **Users → Add user**.
   - Set **Email** (this is also the username, since `registrationEmailAsUsername` is enabled).
   - Set **First name** and **Last name**.
   - Leave **Email verified** unchecked unless you want to skip verification.
   - Click **Create**.

4. Go to the **Credentials** tab of the new user.
   - Click **Set password**.
   - Enter a temporary password, keep **Temporary** toggled ON.
   - Click **Save**.

5. Send the user:
   - The app URL: `https://wacchat.win`
   - Their email address (= login username)
   - The temporary password

   On first login Keycloak will force them to choose their own permanent password.

### Resetting a forgotten password

With SMTP configured, users can use the "Forgot password" link on the login page — Keycloak sends them a reset link automatically. Without SMTP, reset manually:

1. Open the Keycloak admin console → **Users** → select the user.
2. **Credentials** tab → **Set password** (Temporary: ON).
3. Send them the new temporary password privately.

### Revoking access

To block a user without deleting their chat history:

1. **Users** → select the user → toggle **Enabled** to OFF → **Save**.

To remove them entirely:

1. **Users** → select the user → **Delete**.

---

## Project structure

```
wac/
├── backend/
│   └── src/main/java/dev/pioruocco/connecting/
│       ├── chat/           # Chat entity, CRUD, REST controller
│       ├── message/        # Message entity, state machine (SENT/SEEN), media upload
│       ├── user/           # User entity + UserSynchronizer (Keycloak → local DB)
│       ├── notification/   # WebSocket push notifications
│       ├── file/           # File storage service and utilities
│       ├── security/       # SecurityConfig, KeycloakJwtAuthenticationConverter
│       ├── ws/             # WebSocketConfig (STOMP broker)
│       ├── interceptor/    # UserSynchronizerFilter (runs per-request)
│       └── common/         # BaseAuditingEntity, shared DTOs
├── frontend/
│   └── src/app/
│       ├── pages/main/     # Main chat page, WebSocket client
│       ├── components/     # chat-list component
│       ├── services/       # Auto-generated API client (do not edit manually)
│       └── utils/          # KeycloakService, HTTP interceptor
├── database/
│   └── schema.sql          # Reference DDL for users, chat, messages tables
├── keycloak/
│   └── realms/
│       └── connecting.json # Full realm export — imported automatically on Keycloak startup
└── docker-compose-connecting/
    └── docker-compose.yml  # Postgres + Keycloak with persistent volumes
```

---

## Testing

### Backend

```bash
cd wac/backend
./mvnw test                        # all tests
./mvnw test -Dtest=ClassName       # single test class
```

### Frontend

```bash
cd wac/frontend
npm test                           # Karma + Jasmine, runs in Chrome
```

---

## License

[Apache License 2.0](LICENSE)
