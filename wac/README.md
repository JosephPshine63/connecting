# Connecting - WhatsApp Clone

**Connecting** è un'applicazione di chat social media sviluppata in **Spring Boot**. Questo progetto implementa funzionalità di messaggistica in tempo reale con WebSocket, autenticazione OAuth2 e persistenza dei dati con PostgreSQL e JPA.

---

## Tecnologie principali

- **Java 17**
- **Spring Boot 3.4.1**
- **Spring Data JPA**: gestione dei dati e interazione con il database.
- **Spring Web**: sviluppo di REST API.
- **Spring WebSocket**: comunicazione in tempo reale.
- **Spring Security**: sicurezza e gestione autenticazione OAuth2.
- **Spring Validation**: validazione dei dati lato server.
- **PostgreSQL**: database relazionale.
- **Flyway**: gestione delle migrazioni del database.
- **Lombok**: riduzione del boilerplate code.
- **SpringDoc OpenAPI**: generazione automatica della documentazione API.
- **JUnit & Spring Security Test**: test unitari e di sicurezza.

---

## Requisiti

- **Java 17**
- **Maven 3.8+**
- **PostgreSQL** in esecuzione con un database configurato
- (Opzionale) IDE come IntelliJ IDEA, Eclipse o VS Code con supporto Maven

---

## Configurazione del database

Configura le proprietà del database in `src/main/resources/application.properties` o `application.yml`:

```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/connecting
spring.datasource.username=tuo_utente
spring.datasource.password=tuo_password
spring.jpa.hibernate.ddl-auto=validate
spring.jpa.show-sql=true
spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect
