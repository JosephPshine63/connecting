# 📚 Documentation - Documentazione Progetto

Cartella contenente diagrammi architetturali, specifiche tecniche e documentazione visuale dell'applicazione Connecting.

---

## 📁 File Presenti

### whatsapp_clone.drawio
**Diagramma architettura interattivo**

- Creato con [DrawIO](https://draw.io)
- Visualizza l'intera architettura sistema
- Mostra interazioni tra Frontend, Backend, Database, Keycloak
- Componenti principali:
  - **Client Layer** (Angular Frontend)
  - **API Layer** (Spring Boot REST + WebSocket)
  - **Data Layer** (PostgreSQL + JPA)
  - **Auth Layer** (Keycloak OAuth2)

**Come aprire:**
1. Vai a [draw.io](https://draw.io)
2. File → Open → Seleziona `whatsapp_clone.drawio`
3. Oppure importa direttamente da GitHub tramite DrawIO app

### erd.png
**Entity Relationship Diagram (ERD)**

Diagramma che mostra:
- Tutte le tabelle database
- Relazioni tra entità (1:N, M:N)
- Chiavi primarie e foreign
- Attributi principali per tabella

**Tabelle visualizzate:**
- Users (Utenti)
- Chats (Conversazioni)
- Messages (Messaggi)
- Files (Allegati)
- Notifications (Notifiche)
- ChatMembers (Partecipanti)
- UserContacts (Contatti)

### spring-boot-project.txt
**Descrizione testurale architettura**

Documento tecnico che descrive:
- Stack tecnologico
- Struttura progetto
- Dipendenze Maven principali
- Configurazione Spring Boot
- Moduli e package
- Convenzioni di naming

### .$whatsapp_clone.drawio.bkp
**Backup automatico DrawIO**

File di backup creato automaticamente da DrawIO quando si modifica `whatsapp_clone.drawio`. Usato per recuperare versioni precedenti.

---

## 🏗️ Architettura ad Alto Livello

```
┌─────────────────────────────────────────┐
│       FRONTEND (Angular 19)             │
│  - Componenti, Pages, Services          │
│  - WebSocket (SockJS/STOMP)             │
│  - OAuth2 (Keycloak-js)                 │
└────────────┬────────────────────────────┘
             │ HTTP REST + WebSocket
┌────────────▼────────────────────────────┐
│     BACKEND (Spring Boot 3.4.1)         │
│  - REST Controllers (/api/*)            │
│  - WebSocket Handlers (/ws/*)           │
│  - Business Logic (Services)            │
│  - JPA Repositories                     │
│  - OAuth2 Resource Server               │
└────────────┬────────────────────────────┘
             │ JDBC/SQL
┌────────────▼────────────────────────────┐
│    DATABASE (PostgreSQL 16)             │
│  - Users, Chats, Messages               │
│  - Files, Notifications                 │
│  - Contacts, ChatMembers                │
└─────────────────────────────────────────┘
         │ (Parallel)
┌────────▼────────────────────────────────┐
│  KEYCLOAK (OAuth2/OpenID Connect)       │
│  - User Management                      │
│  - Token Generation (JWT)               │
│  - Realm Configuration                  │
└─────────────────────────────────────────┘
```

---

## 🔄 Flusso Dati Principale

### 1. User Login Flow
```
1. Frontend → Keycloak: OAuth2 Authorization Code Grant
2. Keycloak → Frontend: Authorization Code
3. Frontend → Keycloak: Exchange code for token
4. Keycloak → Frontend: Access Token (JWT)
5. Frontend: Salva token in localStorage
```

### 2. Message Send Flow (Real-time)
```
1. User digita messaggio in Frontend
2. Frontend → WebSocket: Send message
3. Backend (WebSocket Handler): Riceve messaggio
4. Backend → Database: Salva message record
5. Backend → WebSocket: Broadcast a destinatari
6. Frontend (WebSocket Client): Visualizza messaggio real-time
```

### 3. Data Fetch Flow (REST)
```
1. Frontend: GET /api/chats
2. Backend: Valida JWT token
3. Backend → Database: JPA query
4. Database: Ritorna dati
5. Backend → Frontend: JSON response
6. Frontend: Aggiorna UI con dati
```

---

## 📊 Database Schema (Sintesi)

```sql
users
├─ id (PK)
├─ username
├─ email
├─ avatar_url
└─ ...

chats
├─ id (PK)
├─ name
├─ is_group
└─ ...

chat_members
├─ id (PK)
├─ chat_id (FK → chats)
├─ user_id (FK → users)
└─ role

messages
├─ id (PK)
├─ chat_id (FK → chats)
├─ sender_id (FK → users)
├─ content
└─ ...

files
├─ id (PK)
├─ message_id (FK → messages)
├─ file_path
└─ ...

notifications
├─ id (PK)
├─ user_id (FK → users)
├─ type
└─ ...

user_contacts
├─ id (PK)
├─ user_id (FK → users)
├─ contact_id (FK → users)
└─ ...
```

---

## 🔐 Security Architecture

### Authentication (OAuth2 con Keycloak)
```
Keycloak issue JWT token
↓
Frontend stores token
↓
Frontend sends token in Authorization header (every request)
↓
Backend validates JWT signature
↓
Backend extracts user claims (sub, email, roles)
↓
Backend grants/denies access based on roles
```

### Authorization (Role-Based Access Control)
```
Token claims include roles:
{
  "sub": "user-uuid",
  "realm_access": {
    "roles": ["user", "admin"]
  }
}
↓
Backend checks @Secured("admin") or hasRole("admin")
↓
Endpoint protected by role
```

---

## 📈 Scaling Considerations

### Database
- **Read Replicas** per query-heavy operations
- **Connection Pooling** (HikariCP)
- **Indexing** su foreign keys e frequently queried columns

### Backend
- **Horizontal Scaling** via load balancer (nginx, HAProxy)
- **Stateless** design (no session affinity needed)
- **Caching** layer (Redis) per ridurre DB queries

### Frontend
- **CDN** per serving assets (CloudFlare, AWS CloudFront)
- **Code Splitting** per ridurre bundle size
- **Service Workers** per offline capability

### WebSocket
- **Redis Pub/Sub** per broadcast tra multiple backend instances
- **Sticky Sessions** per mantenere connessione con stesso backend

---

## 🚀 Deployment Architecture

### Development
```
Docker Compose (localhost)
├─ PostgreSQL
├─ Keycloak
├─ Backend (mvn spring-boot:run)
└─ Frontend (ng serve)
```

### Staging/Production
```
Kubernetes Cluster
├─ PostgreSQL StatefulSet
├─ Keycloak Deployment
├─ Backend Deployment (replicas)
├─ Frontend Deployment (nginx)
└─ Redis Deployment (optional)
```

---

## 📞 API Contracts

### REST Endpoints Principali

```
# Authentication
POST   /auth/login
POST   /auth/logout
POST   /auth/refresh

# Users
GET    /api/users
GET    /api/users/{id}
PUT    /api/users/{id}
DELETE /api/users/{id}

# Chats
GET    /api/chats
POST   /api/chats
GET    /api/chats/{id}
PUT    /api/chats/{id}
DELETE /api/chats/{id}

# Messages
GET    /api/messages?chatId={id}
POST   /api/messages
PUT    /api/messages/{id}
DELETE /api/messages/{id}

# Files
POST   /api/files/upload
GET    /api/files/{id}
DELETE /api/files/{id}

# Notifications
GET    /api/notifications
PUT    /api/notifications/{id}/read
DELETE /api/notifications/{id}
```

### WebSocket Topics

```
# Subscribe
/topic/messages/{chatId}           # Chat messaggi
/user/{userId}/queue/notifications # Notifiche personali

# Send
/app/chat.sendMessage
/app/chat.updateMessage
/app/chat.deleteMessage
```

---

## 🔧 Development Checklist

Quando sviluppi una nuova feature:

- [ ] Update Database Schema (Flyway migration)
- [ ] Create JPA Entity in Backend
- [ ] Create Repository interface
- [ ] Create Service class
- [ ] Create REST Controller
- [ ] Add WebSocket handler (se real-time)
- [ ] Generate OpenAPI docs
- [ ] Create Angular Service per API client
- [ ] Create Angular Component
- [ ] Add Unit Tests
- [ ] Add Integration Tests
- [ ] Update Documentation
- [ ] Update ERD diagram
- [ ] Update Architecture diagram

---

## 📚 Correlazione File/Cartelle

```
├── README.md                    [Sintesi globale - START HERE]
├── wac/
│   ├── backend/
│   │   ├── README.md           [Guide backend spring boot]
│   │   ├── src/main/java/      [Sorgenti Java]
│   │   ├── src/main/resources/ [applicazione.yml, migrations]
│   │   └── pom.xml             [Maven dependencies]
│   ├── frontend/
│   │   ├── README.md           [Guide Angular19]
│   │   ├── src/
│   │   ├── package.json        [npm dependencies]
│   │   └── angular.json        [ng config]
│   ├── database/
│   │   ├── README.md           [Guide PostgreSQL]
│   │   └── schema.sql          [Initial schema]
│   ├── keycloak/
│   │   ├── README.md           [Guide OAuth2/Keycloak]
│   │   └── realms/connecting.json
│   ├── docker-compose/
│   │   ├── README.md           [Guide orchestrazione]
│   │   └── docker-compose.yml
│   └── documentation/
│       ├── README.md           [You are here]
│       ├── whatsapp_clone.drawio [Architecture diagram]
│       ├── erd.png             [Database ERD]
│       └── spring-boot-project.txt
```

---

## 🔗 Link Utili

- **Swagger API Docs:** http://localhost:8080/swagger-ui.html
- **Keycloak Admin:** http://localhost:9090/admin
- **DrawIO Editor:** https://draw.io
- **Official Docs:**
  - [Spring Boot](https://spring.io/projects/spring-boot)
  - [Angular](https://angular.io)
  - [Keycloak](https://www.keycloak.org)
  - [PostgreSQL](https://www.postgresql.org/docs/)

---

## ✍️ Manutenzione Documentazione

Questo folder deve essere aggiornato quando:
- Cambia architettura progetto
- Si aggiungono nuovi moduli/servizi
- Cambiano relazioni database
- Si modifica flow autenticazione
- Si implementano nuovi security patterns

Usa DrawIO per modificare diagrammi e .png per esportare.
