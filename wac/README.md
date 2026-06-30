# Connecting

**Full-stack real-time chat application** built with Spring Boot, Angular, PostgreSQL, and Keycloak. Users authenticate via OAuth2 (Keycloak), exchange messages over WebSocket (STOMP/SockJS), and upload media files through a REST API. Welcome emails are sent via Gmail SMTP on first login.

---

## Features

- Real-time messaging via STOMP/SockJS WebSocket
- OAuth2/OpenID Connect authentication delegated entirely to Keycloak
- Automatic user provisioning: first authenticated request upserts Keycloak JWT claims into local DB
- Welcome email sent on first login (Gmail SMTP)
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

## Local development

### 1. Create `.env`

Copy and fill in your secrets at the repo root (never commit this file):

```bash
POSTGRES_USER=connecting
POSTGRES_PASSWORD=<strong_password>
POSTGRES_DB=connecting_db

KEYCLOAK_ADMIN_USERNAME=admin
KEYCLOAK_ADMIN_PASSWORD=<strong_password>

GOOGLE_CLIENT_ID=<your_google_oauth_client_id>
GOOGLE_CLIENT_SECRET=<your_google_oauth_client_secret>

MAIL_USERNAME=<your_gmail_address>
MAIL_PASSWORD=<gmail_app_password_16_chars>
```

`MAIL_USERNAME` and `MAIL_PASSWORD` are required for welcome emails. Generate a Gmail App Password at **myaccount.google.com → Security → App Passwords**.

### 2. Start infrastructure

```bash
./deploy-local.sh
```

This script:
- Loads `.env`
- Generates `wac/keycloak/realms/connecting.json` from the template
- Starts PostgreSQL on port `5433` and Keycloak on port `8180` via Docker Compose

### 3. Start the backend

**Terminal (recommended — uses [direnv](https://direnv.net/) to auto-load env vars):**

```bash
# One-time setup
echo 'eval "$(direnv hook zsh)"' >> ~/.zshrc   # or ~/.bashrc
source ~/.zshrc
direnv allow   # run once per repo, and again whenever .envrc changes

# Then every time:
cd wac/backend && ./mvnw spring-boot:run
```

**IntelliJ IDEA:**

1. Install the **EnvFile** plugin (Preferences → Plugins → search "EnvFile")
2. Open the Spring Boot run configuration → **EnvFile** tab → enable → add the `.env` at the repo root
3. Run the configuration normally

API available at `http://localhost:8080`. Swagger UI at `http://localhost:8080/swagger-ui.html`.

### 4. Apply database schema (first run only)

```bash
psql -h localhost -p 5433 -U connecting -d connecting_db -f wac/database/schema.sql
```

> Flyway is present as a dependency but disabled. JPA `ddl-auto: update` handles schema drift in dev; apply `schema.sql` on a fresh database.

### 5. Start the frontend

```bash
cd wac/frontend
npm install
npm start
```

App available at `http://localhost:4200`.

---

## Configuration

### Backend — `wac/backend/src/main/resources/application.yml`

Key values are driven by environment variables with sensible defaults:

| Env var | Default | Notes |
|---------|---------|-------|
| `SPRING_DATASOURCE_USERNAME` / `POSTGRES_USER` | `admin` | Either name is accepted |
| `SPRING_DATASOURCE_PASSWORD` / `POSTGRES_PASSWORD` | `admin` | Either name is accepted |
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://localhost:5433/connecting_db` | |
| `KEYCLOAK_ISSUER_URI` | `http://localhost:8180/realms/connecting` | |
| `KEYCLOAK_ADMIN_URL` | `http://keycloak-connecting:8080` | Override to `http://localhost:8180` for local dev |
| `MAIL_USERNAME` | _(empty — disables email)_ | Gmail address |
| `MAIL_PASSWORD` | _(empty — disables email)_ | Gmail App Password |

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

## User management

Self-registration is handled through Keycloak. On first login, the backend automatically provisions the user into the local database and sends a welcome email (if SMTP is configured).

### Inviting a new user (admin-created accounts)

1. Open the Keycloak admin console:
   - **Production:** `https://auth.wacchat.win/admin`
   - **Dev:** `http://localhost:8180/admin`

2. Log in and select the **connecting** realm from the top-left dropdown.

3. Go to **Users → Add user**.
   - Set **Email** (also used as username).
   - Set **First name** and **Last name**.
   - Click **Create**.

4. Go to the **Credentials** tab → **Set password** → enter a temporary password → keep **Temporary** toggled ON → **Save**.

5. Send the user the app URL, email, and temporary password. Keycloak forces a password change on first login.

### Resetting a forgotten password

With SMTP configured, users can use the "Forgot password" link — Keycloak sends a reset link automatically. Without SMTP, reset manually via the Keycloak admin console: **Users → Credentials → Set password (Temporary: ON)**.

### Revoking access

- **Block without deleting history:** Users → select user → toggle **Enabled** to OFF → Save.
- **Delete entirely:** Users → select user → Delete.

---

## Deployment

```bash
./deploy-prod.sh
```

Builds Docker images for backend and frontend, starts PostgreSQL + Keycloak, and runs the containers. Requires `.env` at the repo root with all values populated (including `MAIL_USERNAME` and `MAIL_PASSWORD`).

---

## Project structure

```
wac/
├── backend/
│   └── src/main/java/dev/pioruocco/connecting/
│       ├── chat/           # Chat entity, CRUD, REST controller
│       ├── message/        # Message entity, state machine (SENT/SEEN), media upload
│       ├── user/           # User entity, UserSynchronizer (Keycloak → local DB), MailService
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
└── keycloak/
    └── realms/
        └── connecting.json.template  # Realm template (envsubst fills Google OAuth credentials)
```

Root-level files:

```
deploy-local.sh          # Start infra for local dev
deploy-prod.sh           # Full production deploy (build + run)
docker-compose.yml       # PostgreSQL + Keycloak
docker-compose.local.yml # Local overrides (KC_HOSTNAME = http://localhost:8180)
.envrc                   # direnv: loads .env and maps POSTGRES_* → SPRING_DATASOURCE_*
.env                     # Secrets (never commit — gitignored)
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
