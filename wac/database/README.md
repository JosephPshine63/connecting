# 🗄️ Database - PostgreSQL Schema

Configurazione e schema SQL per il database relazionale **PostgreSQL** che persiste i dati dell'applicazione Connecting.

---

## 📦 Contenuto

- **schema.sql** - Definizione complete tabelle, indici e vincoli
- **Migrazioni Flyway** - Script incrementali gestiti dal backend

---

## 📊 Tabelle Principali

### Users (Utenti)
```sql
CREATE TABLE users (
  id UUID PRIMARY KEY,
  username VARCHAR(255) UNIQUE NOT NULL,
  email VARCHAR(255) UNIQUE NOT NULL,
  phone VARCHAR(20),
  full_name VARCHAR(255),
  bio TEXT,
  avatar_url VARCHAR(512),
  status VARCHAR(50),           -- online, offline, away
  last_seen TIMESTAMP,
  is_active BOOLEAN DEFAULT true,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

### Chats (Conversazioni)
```sql
CREATE TABLE chats (
  id UUID PRIMARY KEY,
  name VARCHAR(255),
  is_group BOOLEAN DEFAULT false,
  description TEXT,
  avatar_url VARCHAR(512),
  created_by UUID REFERENCES users(id),
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

### Chat Members (Partecipanti Chat)
```sql
CREATE TABLE chat_members (
  id UUID PRIMARY KEY,
  chat_id UUID NOT NULL REFERENCES chats(id) ON DELETE CASCADE,
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  role VARCHAR(50),              -- owner, admin, member
  joined_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  UNIQUE(chat_id, user_id)
);
```

### Messages (Messaggi)
```sql
CREATE TABLE messages (
  id UUID PRIMARY KEY,
  chat_id UUID NOT NULL REFERENCES chats(id) ON DELETE CASCADE,
  sender_id UUID NOT NULL REFERENCES users(id),
  content TEXT,
  message_type VARCHAR(50),      -- text, image, file, audio
  status VARCHAR(50),            -- sent, delivered, read
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  deleted_at TIMESTAMP           -- soft delete
);
```

### Files (File Allegati)
```sql
CREATE TABLE files (
  id UUID PRIMARY KEY,
  message_id UUID REFERENCES messages(id) ON DELETE SET NULL,
  uploader_id UUID NOT NULL REFERENCES users(id),
  file_name VARCHAR(255) NOT NULL,
  file_size BIGINT,
  file_type VARCHAR(100),
  file_path VARCHAR(512) NOT NULL,
  mime_type VARCHAR(100),
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

### Notifications (Notifiche)
```sql
CREATE TABLE notifications (
  id UUID PRIMARY KEY,
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  type VARCHAR(50),              -- message, user_online, chat_invite
  title VARCHAR(255),
  body TEXT,
  related_entity_id UUID,
  related_entity_type VARCHAR(50),
  is_read BOOLEAN DEFAULT false,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  read_at TIMESTAMP
);
```

### User Contacts (Contatti)
```sql
CREATE TABLE user_contacts (
  id UUID PRIMARY KEY,
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  contact_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  nickname VARCHAR(255),
  is_blocked BOOLEAN DEFAULT false,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  UNIQUE(user_id, contact_id),
  CHECK (user_id != contact_id)
);
```

---

## 🔑 Indici Principali

```sql
-- Performance su query frequenti
CREATE INDEX idx_messages_chat_id ON messages(chat_id);
CREATE INDEX idx_messages_sender_id ON messages(sender_id);
CREATE INDEX idx_messages_created_at ON messages(created_at DESC);
CREATE INDEX idx_chat_members_chat_id ON chat_members(chat_id);
CREATE INDEX idx_chat_members_user_id ON chat_members(user_id);
CREATE INDEX idx_notifications_user_id ON notifications(user_id);
CREATE INDEX idx_notifications_is_read ON notifications(is_read);
CREATE INDEX idx_user_contacts_user_id ON user_contacts(user_id);
CREATE INDEX idx_files_message_id ON files(message_id);
```

---

## 🚀 Setup Database

### 1. Installazione PostgreSQL

**Linux (Debian/Ubuntu):**
```bash
sudo apt-get update
sudo apt-get install postgresql postgresql-contrib
sudo systemctl start postgresql
```

**macOS (Homebrew):**
```bash
brew install postgresql
brew services start postgresql
```

**Windows:**
Scarica da [postgresql.org](https://www.postgresql.org/download/windows/)

### 2. Creazione Database

```bash
# Connessione al server PostgreSQL
psql -U postgres

# Crea database
CREATE DATABASE connecting;

# Crea utente
CREATE USER connecting_user WITH PASSWORD 'your_secure_password';

# Concedi permessi
GRANT ALL PRIVILEGES ON DATABASE connecting TO connecting_user;

# Esci
\q
```

### 3. Importazione Schema

```bash
# Con psql
psql -U connecting_user -d connecting -f schema.sql

# Oppure da psql prompt
\c connecting
\i schema.sql
```

---

## 🔄 Migrazioni Flyway

Il backend (Spring Boot) gestisce automaticamente le migrazioni SQL tramite **Flyway**.

**Posizione script:** `backend/src/main/resources/db/migration/`

**Naming Convention:** `V{version}__{description}.sql`

Esempio:
```
V1__Initial_schema.sql
V2__Add_notifications_table.sql
V3__Add_user_contacts_table.sql
```

**Flyway si esegue automaticamente al startup del backend:**
```bash
cd backend
mvn spring-boot:run
# Flyway applica automaticamente i migration script
```

---

## 📝 Configurazione Connessione

### Backend (application.yml)
```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/connecting
    username: connecting_user
    password: your_secure_password
    driver-class-name: org.postgresql.Driver
  jpa:
    database-platform: org.hibernate.dialect.PostgreSQLDialect
    hibernate:
      ddl-auto: validate
    show-sql: false
  flyway:
    enabled: true
    locations: classpath:db/migration
    baseline-on-migrate: true
```

### Docker Compose (docker-compose.yml)
```yaml
postgres:
  image: postgres:16-alpine
  environment:
    POSTGRES_DB: connecting
    POSTGRES_USER: connecting_user
    POSTGRES_PASSWORD: your_password
  ports:
    - "5432:5432"
  volumes:
    - postgres_data:/var/lib/postgresql/data
    - ./schema.sql:/docker-entrypoint-initdb.d/schema.sql
```

---

## 🧹 Backup e Restore

### Backup Database

```bash
# Dump completo
pg_dump -U connecting_user -d connecting > backup.sql

# Dump solo schema
pg_dump -U connecting_user -d connecting --schema-only > schema_backup.sql

# Dump solo dati
pg_dump -U connecting_user -d connecting --data-only > data_backup.sql
```

### Restore Database

```bash
# Restore da dump
psql -U connecting_user -d connecting < backup.sql
```

---

## 📊 Query Utili

```sql
-- Numero messaggi per chat
SELECT c.id, c.name, COUNT(m.id) as message_count
FROM chats c
LEFT JOIN messages m ON c.id = m.chat_id
GROUP BY c.id, c.name
ORDER BY message_count DESC;

-- Utenti più attivi
SELECT u.id, u.username, COUNT(m.id) as message_count
FROM users u
LEFT JOIN messages m ON u.id = m.sender_id
GROUP BY u.id, u.username
ORDER BY message_count DESC;

-- Notifiche non lette per utente
SELECT COUNT(*) FROM notifications
WHERE user_id = 'user-uuid' AND is_read = false;

-- Chat member trend
SELECT DATE(created_at) as date, COUNT(*) as new_members
FROM chat_members
GROUP BY DATE(created_at)
ORDER BY date DESC;
```

---

## 🔒 Sicurezza

### Password Sicure
```bash
# Change password utente
ALTER USER connecting_user WITH PASSWORD 'new_secure_password';
```

### Permessi Limitati (Produzione)
```sql
-- Crea utente read-only per report
CREATE USER connecting_readonly WITH PASSWORD 'readonly_password';
GRANT CONNECT ON DATABASE connecting TO connecting_readonly;
GRANT USAGE ON SCHEMA public TO connecting_readonly;
GRANT SELECT ON ALL TABLES IN SCHEMA public TO connecting_readonly;
```

### SSL Connection (Produzione)
```yaml
spring:
  datasource:
    url: jdbc:postgresql://db-host:5432/connecting?sslmode=require&sslcert=...
```

---

## 🐛 Troubleshooting

| Problema | Soluzione |
|----------|-----------|
| "Connection refused" | Verifica PostgreSQL running: `pg_isready` |
| "User/password error" | Controlla credenziali in application.yml |
| "Database doesn't exist" | Crea DB: `createdb -U postgres connecting` |
| "Table doesn't exist" | Esegui schema.sql o verifica Flyway |
| "Timeout" | Aumenta pool connections in application.yml |

---

## 📚 Riferimenti

- [PostgreSQL Official Docs](https://www.postgresql.org/docs/)
- [PostgreSQL JDBC Driver](https://jdbc.postgresql.org/)
- [Flyway Documentation](https://flywaydb.org/documentation/)
