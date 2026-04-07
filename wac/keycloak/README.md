# 🔐 Keycloak - Identity & Access Management

Configurazione **Keycloak 26.0.0** per gestire autenticazione OAuth2/OpenID Connect centralizzata, gestione utenti e autorizzazioni dell'applicazione Connecting.

---

## 📦 Contenuto

- **realms/connecting.json** - Esportazione completa realm Keycloak con configurazioni OAuth2, client app, ruoli e policy

---

## 🔑 Cos'è Keycloak?

**Keycloak** è un Identity Provider open-source che fornisce:
- ✅ Autenticazione OAuth2 / OpenID Connect
- ✅ Gestione centralizzata utenti
- ✅ Single Sign-On (SSO)
- ✅ Gestione ruoli e permessi
- ✅ Social login (Google, GitHub, ecc.)
- ✅ Two-Factor Authentication (2FA)
- ✅ Token JWT con claim personalizzati

---

## 🏗️ Struttura Realm Connecting

### Realm: `connecting`
Realm principale dell'applicazione che contiene:

#### 👥 Client Applications

**1. connecting-frontend** (Public Client - Angular)
```
Type: OpenID Connect
Access Type: public (browser-based)
Redirect URIs: http://localhost:4200/*
Valid Redirect URIs: http://localhost:4200/
Root URL: http://localhost:4200
Web Origins: http://localhost:4200
```

**2. connecting-backend** (Confidential Client - Spring Boot)
```
Type: OpenID Connect
Access Type: confidential
Valid Redirect URIs: http://localhost:8080/callback
Client Secret: [Generato automaticamente]
Service Account: enabled
Token Endpoint Auth Method: client_secret_basic
```

#### 🎭 Roles

**Realm Roles (Globali):**
- `admin` - Amministratore sistema
- `user` - Utente standard
- `moderator` - Moderatore chat

**Client Roles (connecting-backend):**
- `manage-account` - Gestione profilo
- `manage-messages` - Gestione messaggi
- `manage-files` - Gestione file upload

#### 👤 Users Esempio

Pre-configurati nel realm:
- **admin@connecting.local** - Admin account
- **user@connecting.local** - User standard

---

## 🚀 Quick Start

### 1. Avvio Keycloak con Docker

```bash
cd docker-compose
docker-compose up -d keycloak-connecting
```

Keycloak disponibile su: **http://localhost:9090**

Admin Console: **http://localhost:9090/auth/admin**

### 2. Credenziali Admin Iniziali

```
Username: admin
Password: admin123
```

**⚠️ IMPORTANTE:** Cambia password al primo accesso in produzione!

### 3. Accesso Admin Console

1. Vai a `http://localhost:9090/auth/admin`
2. Login con admin/admin123
3. Seleziona realm `connecting` dal dropdown

---

## 📋 Configurazione Dettagliata

### Realm Settings

**General:**
- Realm Name: `connecting`
- Display name: `Connecting Chat Application`
- Enabled: true

**Tokens:**
- Access Token Lifespan: 5 minutes
- Refresh Token Lifespan: 30 days
- Session Timeout: 30 minutes

**Email:**
- From: noreply@connecting.local
- SMTP Server: [Configurare per produzione]

### Client Frontend (connecting-frontend)

1. **Access & Valid Redirects:**
   ```
   http://localhost:4200
   http://localhost:4200/
   http://localhost:4200/*
   ```

2. **Web Origins:**
   ```
   http://localhost:4200
   ```

3. **Mapper OAuth Scopes Allowed:**
   - openid
   - profile
   - email

### Client Backend (connecting-backend)

1. **Credentials Tab:**
   - Salva il `Client Secret` generato

2. **Service Account Roles:**
   ```
   Client Roles (connecting-backend):
   - manage-account
   - manage-messages
   - manage-files
   ```

3. **Mappers:**
   - Role Mapper (per includere ruoli nei claim JWT)
   - Email Mapper (per includere email nei claim)

---

## 🔌 Integrazione Frontend (Angular)

### Configurazione (environment.ts)

```typescript
export const environment = {
  keycloakUrl: 'http://localhost:9090',
  keycloakRealm: 'connecting',
  keycloakClientId: 'connecting-frontend'
};
```

### Servizio AuthService

```typescript
export class AuthService {
  private keycloak = new Keycloak({
    url: environment.keycloakUrl + '/auth',
    realm: environment.keycloakRealm,
    clientId: environment.keycloakClientId
  });

  initialize(): Promise<void> {
    return this.keycloak.init({
      onLoad: 'login-required',
      responseMode: 'fragment'
    });
  }

  login(): void {
    this.keycloak.login();
  }

  logout(): void {
    this.keycloak.logout();
  }

  getToken(): string {
    return this.keycloak.token || '';
  }
}
```

### HTTP Interceptor

```typescript
@Injectable()
export class AuthInterceptor implements HttpInterceptor {
  constructor(private authService: AuthService) {}

  intercept(req: HttpRequest<any>, next: HttpHandler): Observable<HttpEvent<any>> {
    const token = this.authService.getToken();
    
    if (token) {
      req = req.clone({
        setHeaders: {
          Authorization: `Bearer ${token}`
        }
      });
    }

    return next.handle(req);
  }
}
```

---

## 🔌 Integrazione Backend (Spring Boot)

### Dipendenze (pom.xml)

```xml
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-oauth2-resource-server</artifactId>
</dependency>
```

### Configurazione (application.yml)

```yaml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: http://localhost:9090/realms/connecting
          jwk-set-uri: http://localhost:9090/realms/connecting/protocol/openid-connect/certs
```

### Security Config

```typescript
@Configuration
public class SecurityConfig {
  
  @Bean
  public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    http
      .authorizeHttpRequests(authz -> authz
        .requestMatchers("/api/public/**").permitAll()
        .requestMatchers("/api/admin/**").hasRole("admin")
        .anyRequest().authenticated()
      )
      .oauth2ResourceServer(oauth2 -> oauth2
        .jwt(Customizer.withDefaults())
      );
    
    return http.build();
  }
}
```

### Estrazione Claims JWT

```typescript
@RestController
@RequestMapping("/api/users")
public class UserController {
  
  @GetMapping("/me")
  public UserDto getCurrentUser(@AuthenticationPrincipal JwtAuthenticationToken token) {
    String userId = token.getToken().getClaimAsString("sub");
    String email = token.getToken().getClaimAsString("email");
    String username = token.getToken().getClaimAsString("preferred_username");
    
    return new UserDto(userId, username, email);
  }
}
```

---

## 🔐 JWT Token Structure

### Header
```json
{
  "alg": "RS256",
  "typ": "JWT",
  "kid": "..."
}
```

### Payload
```json
{
  "jti": "...",
  "exp": 1704067200,
  "nbf": 0,
  "iat": 1704067200,
  "iss": "http://localhost:9090/realms/connecting",
  "aud": "account",
  "sub": "user-uuid",
  "typ": "Bearer",
  "azp": "connecting-frontend",
  "session_state": "...",
  "acr": "1",
  "allowed-origins": ["http://localhost:4200"],
  "realm_access": {
    "roles": ["user", "default-roles-connecting"]
  },
  "email": "user@connecting.local",
  "email_verified": true,
  "name": "John Doe",
  "preferred_username": "john_doe"
}
```

---

## 👥 Gestione Utenti

### Creare Nuovo Utente

1. Admin Console → Users → Add user
2. Riempi username e email
3. Set Password (Temporary)
4. Assign Roles

### Reset Password

```bash
# Dalla riga di comando
docker exec keycloak-connecting \
  /opt/keycloak/bin/kcadm.sh update users/USER_ID \
  -r connecting -s requiredActions=UPDATE_PASSWORD
```

### Import Utenti Bulk

Usa file JSON con lista utenti e importa via:
Admin Console → Manage → Import

---

## 🔄 Social Login (Opzionale)

### Aggiungere Google Login

1. Admin Console → Identity Providers → Add Google
2. Configura Google OAuth2 credentials
3. Utenti potranno fare login con Google account

### Aggiungere GitHub Login

1. Admin Console → Identity Providers → Add GitHub
2. Configura GitHub OAuth2 App
3. Utenti potranno fare login con GitHub account

---

## 📊 Monitoring e Troubleshooting

### Log Keycloak

```bash
# Visualizza log container
docker logs keycloak-connecting

# Log level config in application.yaml
docker exec keycloak-connecting \
  /opt/keycloak/bin/kcadm.sh update realms/connecting \
  -s notBefore=1 -s publicKey=...
```

### Test Token JWT

```bash
# Decode JWT online (non in produzione!)
# https://jwt.io/

# Oppure con CLI
jwt decode <your_token>
```

### Verifica Realm Configuration

```bash
# GET realm info
curl http://localhost:9090/realms/connecting

# GET JWKS (certificate keys)
curl http://localhost:9090/realms/connecting/protocol/openid-connect/certs

# GET token (Resource Owner Password Flow - solo test)
curl -X POST http://localhost:9090/realms/connecting/protocol/openid-connect/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "client_id=connecting-frontend&grant_type=password&username=user&password=password"
```

---

## 📂 Export/Import Realm

### Esportare Realm (Backup)

```bash
# Via Admin CLI
docker exec keycloak-connecting \
  /opt/keycloak/bin/kcadm.sh get realms/connecting \
  > realms/connecting.json
```

### Importare Realm (Restore)

```bash
# File connecting.json già presente in realms/
# Keycloak lo importa automaticamente al startup
```

---

## 🐛 Troubleshooting

| Problema | Soluzione |
|----------|-----------|
| "401 Unauthorized" | Token scaduto, refresh o re-login |
| "CORS error login" | Verifica CORS settings in realm |
| "Invalid Redirect URI" | Verifica redirect_uri esatto in client config |
| "Signature verification failed" | Clock skew tra server, sincronizza time |
| "User not found" | Crea utente in Admin Console o via API |

---

## 📚 Riferimenti

- [Keycloak Official Docs](https://www.keycloak.org/docs)
- [Keycloak Admin REST API](https://www.keycloak.org/docs/latest/server_admin/index.html#admin-rest-api)
- [Spring Boot OAuth2 Integration](https://docs.spring.io/spring-boot/docs/current/reference/html/web-security.html#web.security.oauth2.server)
