# 🐳 Docker Compose Connecting - Configurazione Alternativa

Configurazione **Docker Compose alternativa** per orchestrare i servizi principali in ambiente di sviluppo senza volumi persistenti.

---

## 📦 Differenza dalla Cartella docker-compose/

| Aspetto | docker-compose/ | docker-compose-connecting/ |
|---------|-----------------|---------------------------|
| **Persistenza DB** | ✅ Volume persistente | ❌ In-memory (reset restart) |
| **Uso** | Sviluppo a lungo termine | Test rapidi, CI/CD |
| **Stato tra restart** | Mantiene dati | Perde dati |
| **Performance** | Normale | Più veloce (no sync disk) |

---

## 📋 Servizi Orchestrati

Questo `docker-compose.yml` avvia gli stessi servizi di `../docker-compose/`:

1. **PostgreSQL 16** - Database relazionale
   - Port: `5432`
   - Database: `connecting`
   - ⚠️ Dati **non persistenti** (persi al down)

2. **Keycloak 26.0.0** - Identity Provider OAuth2
   - Port: `9090`
   - Realm pre-caricato: `connecting`
   - Admin: admin / admin123

---

## 🚀 Quick Start

### Avviare i Servizi

```bash
cd docker-compose-connecting
docker-compose up -d
```

### Arrestare i Servizi

```bash
docker-compose down
```

### Reset Completo (inclusi dati)

```bash
docker-compose down -v
docker-compose up -d
# PostgreSQL inizia vuoto, Keycloak importa il realm
```

---

## ⚙️ Configurazione

### PostgreSQL

```yaml
postgres:
  image: postgres:16-alpine
  environment:
    POSTGRES_DB: connecting
    POSTGRES_USER: postgres
    POSTGRES_PASSWORD: postgres
  ports:
    - "5432:5432"
  networks:
    - connecting
  # NO volumes defined - dati non persistono
```

### Keycloak

```yaml
keycloak:
  image: keycloak/keycloak:26.0.0
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

---

## 🎯 Quando Usare

### ✅ USA docker-compose-connecting/ per:

- **Test rapidi** - Spin up/down veloce senza dati storici
- **CI/CD Pipeline** - Test automatici con ambiente pulito
- **Sviluppo feature** - Isolato da dati development
- **Demo/POC** - Reset facile tra dimostrazioni
- **Debugging** - Partire da stato noto/pulito

### ✅ USA docker-compose/ per:

- **Sviluppo continuativo** - Mantieni dati tra sessioni
- **QA testing** - Test con dataset realista persistente
- **Produzione-like** - Simula ambiente prod con dati

---

## 📝 Workflow Tipico

### Test su Ambiente Pulito

```bash
# 1. Start servizi con env pulito
cd docker-compose-connecting
docker-compose up -d

# 2. Backend automaticamente Flyway migrazioni
cd ../backend
mvn clean package
mvn spring-boot:run

# 3. Frontend connette a backend
cd ../frontend
npm install
npm start

# 4. Test feature nuova
# ... testo tutto ...

# 5. Reset per test successivo
cd ../docker-compose-connecting
docker-compose down -v
docker-compose up -d
```

### CI/CD Integration

```yaml
# .github/workflows/test.yml
steps:
  - name: Start services
    run: |
      cd docker-compose-connecting
      docker-compose up -d

  - name: Run tests
    run: |
      cd backend
      mvn clean test
      
  - name: Cleanup
    if: always()
    run: |
      cd docker-compose-connecting
      docker-compose down -v
```

---

## 🔄 Migrazioni Database

**Attenzione:** Ogni volta che fai `docker-compose down -v`, il database è completamente reset.

Keycloak applica automaticamente:
1. ✅ Schema creazione (da backend Flyway)
2. ✅ Realm import (da `../keycloak/realms/connecting.json`)

Non è richiesta alcuna azione manuale.

---

## 📊 Monitoring

### Visualizzare Log

```bash
docker-compose logs -f postgres
docker-compose logs -f keycloak
```

### Verifica Servizi

```bash
docker-compose ps
```

### Health Check

```bash
# PostgreSQL
docker exec $(docker-compose ps -q postgres) pg_isready -U postgres

# Keycloak
curl -s http://localhost:9090/realms/connecting
```

---

## 🐛 Troubleshooting

| Problema | Soluzione |
|----------|-----------|
| Keycloak non replica realm al startup | Verifica path `../keycloak/realms/` esiste e realm JSON valido |
| PostgreSQL mostra "table doesn't exist" | Normale, aspetta backend Flyway. Tenta `docker-compose logs postgres` |
| Dati persistono (indesiderato) | Usa `docker-compose down -v` (remove volumes) |
| Connection refused | Aspetta 10-15 sec dopo `up`, servizi need init time |

---

## 🔗 Riferimenti Correlati

- **docker-compose/** - Versione con persistenza dati
- **backend/src/main/resources/db/migration/** - Flyway migrations
- **keycloak/realms/connecting.json** - Realm configuration
- **database/README.md** - Database schema documentation

---

## 💡 Opzioni Avanzate

### Personalizzare Porta PostgreSQL

```yaml
postgres:
  ports:
    - "5433:5432"  # Usa 5433 se 5432 occupata
```

### Override Environment

```bash
POSTGRES_PASSWORD=custom_password docker-compose up -d
```

### Logs Persistenti

```bash
# Salva output in file (no volumes per DB)
docker-compose logs > docker.log
```

---

## 📚 Quick Links

- [Docker Compose Docs](https://docs.docker.com/compose/)
- [PostgreSQL Image](https://hub.docker.com/_/postgres)
- [Keycloak Guides](https://www.keycloak.org/docs/latest/server_admin/)
