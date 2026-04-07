# 🔧 Backend - Spring Boot API

Applicazione **Spring Boot 3.4.1** che fornisce REST API e WebSocket per la messaggistica in tempo reale, la gestione degli utenti e il sistema di notifiche.

---

## 📦 Tecnologie

- **Java 17**
- **Spring Boot 3.4.1**
- **Spring Data JPA** - ORM con Hibernate
- **Spring Web** - REST API
- **Spring WebSocket** - Comunicazione real-time
- **Spring Security** - OAuth2 Resource Server (Keycloak)
- **PostgreSQL** - Database
- **Flyway** - Migrazioni database
- **Lombok** - Riduzione boilerplate
- **SpringDoc OpenAPI** - Documentazione API (Swagger)

---

## 📁 Struttura

```
src/main/java/dev/pioruocco/connecting/
├── ConnectingApiApplication.java       [Entry point]
├── chat/                               [Gestione chat e conversazioni]
│   ├── controller/
│   ├── service/
│   ├── entity/
│   └── dto/
├── common/                             [Utilities, exception handlers]
│   ├── dto/
│   ├── exception/
│   └── util/
├── file/                               [Upload e download file]
│   ├── controller/
│   ├── service/
│   └── entity/
├── interceptor/                        [Interceptor HTTP, logging]
├── message/                            [Logica messaggi]
│   ├── controller/
│   ├── service/
│   ├── entity/
│   └── dto/
├── notification/                       [Sistema notifiche]
│   ├── controller/
│   ├── service/
│   └── entity/
├── security/                           [Configurazione OAuth2/JWT]
│   ├── SecurityConfig.java
│   └── JwtConverter.java
├── user/                               [Gestione profili utenti]
│   ├── controller/
│   ├── service/
│   ├── entity/
│   └── dto/
└── ws/                                 [WebSocket handlers]
    ├── WebSocketConfig.java
    ├── StompPrincipalHandshakeInterceptor.java
    └── MessageWebSocketHandler.java

src/main/resources/
├── application.yml                     [Configurazione principale]
├── db/migration/                       [Script Flyway]
└── static/                             [Assets statici]

src/test/                               [Test JUnit, Security, Integration]
```

---

## 🚀 Avvio

### Prerequisiti
- Java 17+
- Maven 3.8+
- PostgreSQL in esecuzione

### Setup Database

Configura `application.yml`:

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/connecting
    username: postgres
    password: your_password
  jpa:
    hibernate:
      ddl-auto: validate
    show-sql: false
  flyway:
    enabled: true
    locations: classpath:db/migration
```

### Avvio Applicazione

```bash
mvn clean install
mvn spring-boot:run
```

L'API sarà disponibile su `http://localhost:8080`

---

## 🔌 Endpoint Principali

### Autenticazione
- `POST /auth/login` - Login OAuth2
- `POST /auth/logout` - Logout
- `POST /auth/refresh` - Refresh token

### Utenti
- `GET /api/users` - Lista utenti
- `GET /api/users/{id}` - Dettagli utente
- `PUT /api/users/{id}` - Aggiorna profilo
- `DELETE /api/users/{id}` - Elimina utente

### Chat
- `GET /api/chats` - Lista chat dell'utente
- `POST /api/chats` - Crea nuova chat
- `GET /api/chats/{id}` - Dettagli chat
- `PUT /api/chats/{id}` - Aggiorna chat
- `DELETE /api/chats/{id}` - Elimina chat

### Messaggi
- `GET /api/messages` - Lista messaggi
- `POST /api/messages` - Invia messaggio
- `PUT /api/messages/{id}` - Modifica messaggio
- `DELETE /api/messages/{id}` - Elimina messaggio

### File
- `POST /api/files/upload` - Carica file
- `GET /api/files/{id}` - Scarica file
- `DELETE /api/files/{id}` - Elimina file

### Notifiche
- `GET /api/notifications` - Lista notifiche
- `PUT /api/notifications/{id}/read` - Marca come letto
- `DELETE /api/notifications/{id}` - Elimina notifica

---

## 🔐 WebSocket

### Connessione
```
WS: ws://localhost:8080/ws
Protocol: STOMP over SockJS
```

### Topic di Subscribe
- `/user/{userId}/queue/notifications` - Notifiche personali
- `/topic/messages/{chatId}` - Messaggi della chat

### Invio Messaggi
```
POST /app/chat.sendMessage
{
  "chatId": "uuid",
  "content": "Ciao!",
  "sender": "user-uuid"
}
```

---

## 📚 Documentazione API

Swagger UI disponibile a: `http://localhost:8080/swagger-ui.html`

API Docs JSON: `http://localhost:8080/v3/api-docs`

OpenAPI YAML: `http://localhost:8080/v3/api-docs.yaml`

---

## 🔒 Sicurezza

- **OAuth2 Resource Server** - Token validati da Keycloak
- **JWT** - JSON Web Token per autorizzazione stateless
- **CORS** - Configurato per frontend locale
- **HTTPS** - Consigliato in produzione

---

## 🧪 Test

```bash
# Esegui tutti i test
mvn test

# Test specifico
mvn test -Dtest=UserControllerTest

# Con coverage
mvn jacoco:report
```

---

## 📝 Configurazione Avanzata

### Logging
```yaml
logging:
  level:
    dev.pioruocco.connecting: DEBUG
    org.springframework.security: DEBUG
    org.springframework.web: INFO
```

### WebSocket
```yaml
spring:
  websocket:
    allowed-origins: http://localhost:4200
    heartbeat: 25000
```

### Upload File
```yaml
file:
  upload-dir: ./uploads
  max-size: 10485760  # 10 MB
  allowed-types: image,pdf,document
```

---

## 🐛 Troubleshooting

| Problema | Soluzione |
|----------|-----------|
| "Connection refused" al DB | Verifica PostgreSQL running, credenziali in application.yml |
| Token JWT non valido | Controlla Keycloak e clock skew |
| WebSocket timeout | Aumenta heartbeat in application.yml |
| File upload fallisce | Verifica cartella uploads/ e permessi |

---

## 📄 Riferimenti

- [Apache Maven Documentation](https://maven.apache.org/guides/index.html)
- [Spring Boot 3.4.1 Documentation](https://docs.spring.io/spring-boot/3.4.1/reference/)
- [Spring Data JPA](https://docs.spring.io/spring-boot/3.4.1/reference/data/sql.html#data.sql.jpa-and-spring-data)
- [Spring WebSocket](https://docs.spring.io/spring-boot/3.4.1/reference/messaging/websockets.html)
- [Spring Security OAuth2](https://docs.spring.io/spring-boot/3.4.1/reference/web/spring-security.html)

