# 🚀 Connecting - WhatsApp Clone

Un'applicazione **full-stack di messaggistica social** moderna e scalabile, che implementa chat in tempo reale, autenticazione OAuth2 centralizzata, e persistenza dati relazionale. Sviluppata con **Spring Boot 3.4.1** (backend), **Angular 19** (frontend), **PostgreSQL** (database) e **Keycloak 26.0.0** (autenticazione).

---

## 📋 Panoramica Architettura

```
┌─────────────────────────────────┐
│   FRONTEND (Angular 19)         │
│   • Chat in tempo reale         │
│   • WebSocket (SockJS/STOMP)    │
│   • OAuth2 via Keycloak         │
└────────────┬────────────────────┘
             │ REST API + WebSocket
┌────────────▼────────────────────┐
│   BACKEND (Spring Boot 3.4.1)   │
│   • REST API                    │
│   • WebSocket handlers          │
│   • OAuth2 Resource Server      │
└────────────┬────────────────────┘
             │ JDBC/SQL
┌────────────▼────────────────────┐
│  DATABASE (PostgreSQL)          │
│  • Utenti, Chat, Messaggi       │
│  • Flyway migrations            │
└─────────────────────────────────┘
```

---

## 🛠️ Tecnologie Principali

### Backend
- **Java 17** + **Spring Boot 3.4.1**
- Spring Data JPA, Spring Web, Spring WebSocket
- Spring Security (OAuth2 Resource Server)
- PostgreSQL + Flyway (migrazioni)
- SpringDoc OpenAPI (Swagger)

### Frontend
- **Angular 19** + **TypeScript**
- Keycloak-js (OAuth2)
- SockJS + STOMP (WebSocket)
- Bootstrap 5 + FontAwesome
- ng-openapi-gen (auto-generazione API client)

### Infrastruttura
- **PostgreSQL**: database relazionale
- **Keycloak 26.0.0**: Identity Provider OAuth2/OpenID Connect
- **Docker & Docker Compose**: containerizzazione

---

## 📁 Struttura del Progetto

| Cartella | Descrizione |
|----------|-------------|
| **backend/** | Spring Boot API, WebSocket handlers, logica aziendale |
| **frontend/** | Applicazione Angular, UI e client WebSocket |
| **database/** | Schema SQL e configurazione PostgreSQL |
| **keycloak/** | Configurazione realm Keycloak per OAuth2 |
| **docker-compose/** | Orchestrazione servizi (PostgreSQL, Keycloak) |
| **documentation/** | Diagrammi architettura, ERD, specifiche |

📖 Vedi il README in ogni cartella per dettagli specifici.

---

## 🚀 Quick Start

### Prerequisiti
- **Java 17+**
- **Node.js 20+**
- **Docker & Docker Compose**
- **Maven 3.8+**

### Avvio con Docker Compose

```bash
cd docker-compose
docker-compose up -d
```

Questo avvia:
- 🗄️ PostgreSQL (port 5432)
- 🔐 Keycloak (port 9090)

### Avvio Backend

```bash
cd backend
mvn clean install
mvn spring-boot:run
```

API disponibile: `http://localhost:8080`
Swagger/OpenAPI: `http://localhost:8080/swagger-ui.html`

### Avvio Frontend

```bash
cd frontend
npm install
ng serve
```

App disponibile: `http://localhost:4200`

---

## ⚙️ Configurazione

### Backend (application.yml)
```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/connecting
    username: postgres
    password: your_password
  jpa:
    hibernate:
      ddl-auto: validate
```

### Frontend (environment.ts)
```typescript
export const environment = {
  apiUrl: 'http://localhost:8080/api',
  keycloakUrl: 'http://localhost:9090',
  wsUrl: 'ws://localhost:8080/ws'
};
```

---

## ✨ Funzionalità Principali

✅ **Chat in tempo reale** via WebSocket  
✅ **Autenticazione OAuth2** centralizzata (Keycloak)  
✅ **Gestione contatti** e profili utente  
✅ **Notifiche** in tempo reale  
✅ **Upload/Download file** via REST API  
✅ **API RESTful** ben documentata (OpenAPI/Swagger)  
✅ **Persistenza relazionale** con PostgreSQL + JPA  

---

## 📚 Documentazione Aggiuntiva

- **Backend API**: `backend/README.md`
- **Frontend**: `frontend/README.md`
- **Database**: `database/README.md`
- **Autenticazione**: `keycloak/README.md`
- **Docker**: `docker-compose/README.md`

---

## 📄 Licenza

Vedi `LICENSE` per dettagli.
