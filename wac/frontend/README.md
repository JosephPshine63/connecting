# 💻 Frontend - Angular 19 Application

Applicazione **Angular 19** moderna e responsiva che fornisce l'interfaccia utente per la messaggistica in tempo reale, la gestione contatti e i profili utente.

---

## 📦 Tecnologie

- **Angular 19** - Framework frontend
- **TypeScript** - Linguaggio di programmazione
- **Bootstrap 5** - Framework CSS per responsive design
- **FontAwesome** - Icone
- **Keycloak-js** - OAuth2/OpenID Connect client
- **SockJS** - WebSocket fallback library
- **STOMP** - Messaging protocol su WebSocket
- **ng-openapi-gen** - Auto-generazione API client da OpenAPI spec
- **RxJS** - Reactive programming library
- **Angular Forms** - Reactive forms e template-driven forms

---

## 📁 Struttura

```
src/
├── app/
│   ├── app.component.ts              [Componente root]
│   ├── app.component.html
│   ├── app.component.scss
│   ├── app.config.ts                 [Configurazione globale]
│   ├── app.routes.ts                 [Routing]
│   ├── components/                   [Componenti riutilizzabili]
│   ├── pages/                        [Pagine principali]
│   ├── services/                     [Servizi]
│   ├── utils/                        [Utility functions e helper]
│   ├── guards/                       [Route guards]
│   ├── interceptors/                 [HTTP interceptors]
│   └── openapi/                      [Generated API client da OpenAPI]
├── environments/
│   ├── environment.ts                [Config sviluppo]
│   └── environment.prod.ts           [Config produzione]
├── index.html                        [HTML principale]
├── main.ts                           [Bootstrap applicazione]
└── styles.scss                       [Stili globali]
```

---

## 🚀 Avvio

### Prerequisiti
- Node.js 20+
- npm o yarn
- Angular CLI 19+

### Installazione

```bash
npm install
```

### Configurazione

Modifica `src/environments/environment.ts`:

```typescript
export const environment = {
  production: false,
  apiUrl: 'http://localhost:8080/api',
  keycloakUrl: 'http://localhost:9090',
  keycloakRealm: 'connecting',
  keycloakClientId: 'connecting-frontend',
  wsUrl: 'ws://localhost:8080/ws'
};
```

### Sviluppo

```bash
ng serve
```

Applicazione disponibile su `http://localhost:4200`

### Build Produzione

```bash
ng build --configuration production
```

---

## 🔌 Servizi Principali

- **AuthService** - Autenticazione OAuth2 con Keycloak
- **WebSocketService** - Gestione WebSocket STOMP
- **ChatService** - Gestione chat (HTTP)
- **MessageService** - Gestione messaggi (HTTP + WebSocket)
- **NotificationService** - Notifiche in tempo reale
- **UserService** - Gestione utenti
- **FileService** - Upload/download file

---

## 🔐 Autenticazione

Usa OAuth2 con Keycloak. Token JWT incluso automaticamente in ogni request tramite HTTP interceptor.

---

## 🔄 WebSocket Real-Time

Connessione STOMP per messaggi in tempo reale:
- Topic: `/topic/messages/{chatId}` - Messaggi chat
- Topic: `/user/{userId}/queue/notifications` - Notifiche personali

---

## 🎨 Stili

- Bootstrap 5 per layout responsivo
- SCSS con variabili personalizzate
- FontAwesome 6 per icone

---

## 🧪 Test

```bash
ng test          # Test unitari
ng e2e          # Test E2E
```

---

## 🚀 Deployment

### Static Hosting
```bash
ng build --configuration production
# Carica dist/ verso provider (Netlify, Vercel, GitHub Pages)
```

### Docker
```dockerfile
FROM node:20-alpine as build
WORKDIR /app
COPY package*.json ./
RUN npm install
COPY . .
RUN npm run build

FROM nginx:alpine
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
```

---

## 📦 Dipendenze Principali

- @angular/common, @angular/core, @angular/forms, @angular/router
- keycloak-js, sockjs-client, stompjs
- bootstrap, @fortawesome/fontawesome-free
- rxjs

Vedi `package.json` per elenco completo.
