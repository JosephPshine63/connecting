# 🐳 Docker Compose - Orchestrazione Servizi

Configurazione **Docker Compose** per orchestrare i servizi principali (PostgreSQL, Keycloak) in ambiente di sviluppo.

---

## 📦 Servizi Orchestrati

Questo `docker-compose.yml` avvia:

1. **PostgreSQL 16** - Database relazionale
   - Port: `5432`
   - Volume persistente: `postgres_data`
   - Database: `connecting`

2. **Keycloak 26.0.0** - Identity Provider OAuth2
   - Port: `9090`
   - Realm pre-caricato: `connecting` (da `../keycloak/realms/`)
   - Admin: admin / admin123

---

## 🚀 Quick Start

### Prerequisiti

- **Docker** 20.10+
- **Docker Compose** 2.0+

Verifica installazione:
```bash
docker --version
docker-compose --version
```

### Avviare i Servizi

```bash
cd docker-compose
docker-compose up -d
```

Servizi disponibili:
- PostgreSQL: `localhost:5432`
- Keycloak: `http://localhost:9090`

### Arrestare i Servizi

```bash
docker-compose down
```

Rimuovere anche volumi:
```bash
docker-compose down -v
```

---

## 📋 Configurazione Services

### PostgreSQL

```yaml
postgres:
  image: postgres:16-alpine
  container_name: connecting-db
  environment:
    POSTGRES_DB: connecting
    POSTGRES_USER: postgres
    POSTGRES_PASSWORD: postgres
  ports:
    - "5432:5432"
  volumes:
    - postgres_data:/var/lib/postgresql/data
  networks:
    - connecting
```

**Accesso dal backend:**
```yaml
spring:
  datasource:
    url: jdbc:postgresql://postgres:5432/connecting
    username: postgres
    password: postgres
```

**Accesso locale:**
```bash
psql -h localhost -U postgres -d connecting
```

### Keycloak

```yaml
keycloak:
  image: keycloak/keycloak:26.0.0
  container_name: keycloak-connecting
  environment:
    KEYCLOAK_ADMIN: admin
    KEYCLOAK_ADMIN_PASSWORD: admin123
    KC_PROXY: edge
    KC_HTTP_ENABLED: "true"
  ports:
    - "9090:8080"
  command: start-dev
  volumes:
    - ../keycloak/realms:/opt/keycloak/data/import
  networks:
    - connecting
  depends_on:
    - postgres
```

**Admin Console:** `http://localhost:9090/admin`

**OpenID Config:** `http://localhost:9090/realms/connecting/.well-known/openid-configuration`

---

## 🌐 Network

Tutti i servizi sono connessi alla network `connecting`:

```yaml
networks:
  connecting:
    driver: bridge
```

Questo permette la comunicazione:
- Backend → PostgreSQL: `jdbc:postgresql://postgres:5432/connecting`
- Backend → Keycloak: `http://keycloak:8080/realms/...`
- Localhost → Services: `localhost:5432`, `localhost:9090`

---

## 💾 Volumi Persistenti

### postgres_data

```yaml
volumes:
  postgres_data:
    driver: local
```

**Localizzazione reale:**
- Linux: `/var/lib/docker/volumes/docker-compose_postgres_data/_data`
- macOS: `~/Library/Containers/com.docker.docker/Data/vms/0/data/docker/volumes/...`
- Windows: `\\wsl$\docker-desktop-data\mnt\wsl\docker\volumes\...`

**Backup volumi:**
```bash
docker run --rm -v docker-compose_postgres_data:/data \
  -v $(pwd):/backup \
  alpine tar czf /backup/postgres-backup.tar.gz /data

docker run --rm -v docker-compose_postgres_data:/data \
  -v $(pwd):/backup \
  alpine tar xzf /backup/postgres-backup.tar.gz -C /
```

---

## 🔧 Comandi Utili

### Visualizzare Log

```bash
# Tutti i servizi
docker-compose logs -f

# Singolo servizio
docker-compose logs -f postgres
docker-compose logs -f keycloak

# Ultimi 50 righe
docker-compose logs --tail=50
```

### Eseguire Comando in Container

```bash
# PostgreSQL
docker exec -it connecting-db psql -U postgres -d connecting

# Keycloak
docker exec -it keycloak-connecting /opt/keycloak/bin/kcadm.sh
```

### Status Servizi

```bash
docker-compose ps
```

### Restart Servizio

```bash
docker-compose restart postgres
docker-compose restart keycloak
```

---

## 📊 Monitoring

### Health Check

```bash
# PostgreSQL
docker exec connecting-db pg_isready -U postgres

# Keycloak
curl -s http://localhost:9090/health | jq
```

### Risorse Utilizzate

```bash
docker stats connecting-db keycloak-connecting
```

---

## 🔄 Development Workflow

### 1. Primo Avvio

```bash
cd docker-compose
docker-compose up -d
# Aspetta 30-60 secondi per Keycloak init
```

### 2. Verificare Servizi

```bash
# Check PostgreSQL
docker exec connecting-db psql -U postgres -c "SELECT version();"

# Check Keycloak
curl http://localhost:9090/realms/connecting
```

### 3. Avviare Backend

```bash
cd backend
mvn spring-boot:run
# Backend si connette a postgres:5432 e valida OAuth2 con Keycloak
```

### 4. Avviare Frontend

```bash
cd frontend
npm install
npm start
# Frontend disponibile su http://localhost:4200
```

---

## 🐛 Troubleshooting

| Problema | Soluzione |
|----------|-----------|
| "Port already in use" | `docker-compose down`, libera porta, riavvia |
| Keycloak non parte | Verifica log: `docker-compose logs keycloak` |
| Backend non connette a DB | Verifica hostname: deve essere `postgres` non `localhost` |
| Volume non sincronizzato | `docker-compose down -v`, riavvia pulito |
| Container stopped | `docker-compose up -d` avvia background |

---

## 📝 Customizzazione

### Cambiare Porte

Modifica `docker-compose.yml`:
```yaml
postgres:
  ports:
    - "5433:5432"    # Frontend port: backend port

keycloak:
  ports:
    - "9091:8080"
```

### Cambiare Credenziali

```yaml
postgres:
  environment:
    POSTGRES_PASSWORD: my_secure_password

keycloak:
  environment:
    KEYCLOAK_ADMIN_PASSWORD: my_admin_password
```

### Aggiungere Servizi Aggiuntivi

Esempio aggiungere Redis:
```yaml
redis:
  image: redis:7-alpine
  ports:
    - "6379:6379"
  networks:
    - connecting
```

---

## 🚀 Deployment Produzione

### Docker Registry

```bash
# Build custom image
docker build -t connecting/backend:latest ./backend
docker build -t connecting/frontend:latest ./frontend

# Push to registry
docker tag connecting/backend:latest myregistry/connecting/backend:latest
docker push myregistry/connecting/backend:latest
```

### Production docker-compose.yml

```yaml
version: '3.8'
services:
  postgres:
    image: postgres:16-alpine
    environment:
      POSTGRES_DB: connecting
      POSTGRES_USER: ${DB_USER}
      POSTGRES_PASSWORD: ${DB_PASSWORD}
    volumes:
      - postgres_data:/var/lib/postgresql/data
    networks:
      - connecting
    restart: always
    
  keycloak:
    image: keycloak/keycloak:26.0.0
    environment:
      KEYCLOAK_ADMIN: ${KEYCLOAK_ADMIN}
      KEYCLOAK_ADMIN_PASSWORD: ${KEYCLOAK_PASSWORD}
      KC_PROXY: edge
    volumes:
      - ./keycloak/realms:/opt/keycloak/data/import
    networks:
      - connecting
    restart: always

volumes:
  postgres_data:

networks:
  connecting:
```

Avvia con file `.env`:
```bash
docker-compose -f docker-compose.prod.yml --env-file .env up -d
```

---

## 📚 Riferimenti

- [Docker Compose Docs](https://docs.docker.com/compose/)
- [Docker Compose File Reference](https://docs.docker.com/compose/compose-file/)
- [PostgreSQL Docker Image](https://hub.docker.com/_/postgres)
- [Keycloak Docker Image](https://quay.io/repository/keycloak/keycloak)
