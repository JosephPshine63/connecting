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
│   │   ├── chat-list/                [Lista chat]
│   │   ├── message-input/            [Input messaggi]
│   │   ├── user-profile/             [Profilo utente]
│   │   ├── notification-toast/       [Notifiche]
│   │   └── ...
│   ├── pages/                        [Pagine principali]
│   │   ├── chat-page/                [Pagina chat]
│   │   ├── contacts-page/            [Pagina contatti]
│   │   ├── profile-page/             [Pagina profilo]
│   │   ├── login-page/               [Pagina login]
│   │   └── home/                     [Dashboard]
│   ├── services/                     [Servizi]
│   │   ├── auth.service.ts           [Autenticazione Keycloak]
│   │   ├── websocket.service.ts      [Gestione WebSocket]
│   │   ├── chat.service.ts           [Gestione chat (HTTP)]
│   │   ├── message.service.ts        [Gestione messaggi (HTTP)]
│   │   ├── user.service.ts           [Gestione utenti (HTTP)]
│   │   ├── notification.service.ts   [Notifiche in tempo reale]
│   │   └── file.service.ts           [Upload/download file]
│   ├── utils/                        [Utility functions e helper]
│   │   ├── constants.ts              [Costanti applicazione]
│   │   └── helpers.ts                [Funzioni helper]
│   ├── guards/                       [Route guards]
│   │   └── auth.guard.ts             [Protezione rotte autenticate]
│   ├── interceptors/                 [HTTP interceptors]
│   │   ├── auth.interceptor.ts       [Aggiunge token nei header]
│   │   └── error.interceptor.ts      [Gestione errori HTTP]
│   └── openapi/                      [Generated API client da OpenAPI]
│       ├── models/
│       └── services/
├── environments/
│   ├── environment.ts                [Config sviluppo]
│   └── environment.prod.ts           [Config produzione]
├── index.html                        [HTML principale]
├── main.ts                           [Bootstrap applicazione]
├── styles.scss                       [Stili globali]
└── public/                           [Assets statici]

node_modules/                         [Dipendenze npm]
package.json                          [Dipendenze e script]
angular.json                          [Config Angular CLI]
tsconfig.json, tsconfig.app.json     [Config TypeScript]
.editorconfig                         [Configurazione editor]
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
# Dev server con hot reload
ng serve

# Oppure
npm start
```

Applicazione disponibile su `http://localhost:4200`

### Build Produzione

```bash
# Build ottimizzato
ng build --configuration production

# Oppure
npm run build
```

Output in `dist/` pronto per deployment.

---

## 🔌 Struttura dei Servizi

### AuthService
Gestisce autenticazione OAuth2 con Keycloak:
```typescript
login(): void
logout(): void
isAuthenticated(): boolean
getToken(): string
getCurrentUser(): User
```

### WebSocketService
Gestisce connessione WebSocket STOMP:
```typescript
connect(): Observable<void>
disconnect(): void
subscribe(topic: string): Observable<any>
send(destination: string, body: any): void
```

### ChatService
Comunicazione HTTP per chat:
```typescript
getChats(): Observable<Chat[]>
createChat(chat: ChatDto): Observable<Chat>
getChat(id: string): Observable<Chat>
updateChat(id: string, chat: ChatDto): Observable<Chat>
deleteChat(id: string): Observable<void>
```

### MessageService
Gestione messaggi via HTTP e WebSocket:
```typescript
getMessages(chatId: string): Observable<Message[]>
sendMessage(message: MessageDto): Observable<Message>
updateMessage(id: string, message: MessageDto): Observable<Message>
deleteMessage(id: string): Observable<void>
onMessageReceived$(): Observable<Message>
```

### NotificationService
Sistema notifiche in tempo reale:
```typescript
getNotifications(): Observable<Notification[]>
markAsRead(id: string): Observable<void>
deleteNotification(id: string): Observable<void>
onNotificationReceived$(): Observable<Notification>
```

---

## 🔐 Autenticazione

### Flow OAuth2 (Keycloak)

1. Utente clicca "Login"
2. Reindirizzamento a Keycloak
3. Autenticazione utente
4. Callback con authorizationCode
5. Exchange code → access token
6. Salvataggio token in localStorage/sessionStorage
7. Token inserito in ogni request HTTP via interceptor

### AuthGuard

Protegge le rotte non pubbliche:
```typescript
canActivate(route: ActivatedRouteSnapshot): boolean {
  return this.authService.isAuthenticated();
}
```

---

## 🔄 WebSocket Real-Time

### Connessione STOMP

```typescript
// Nel AppComponent o servizio
this.websocketService.connect().subscribe(() => {
  // Subscribe a topic per messaggi
  this.websocketService.subscribe('/topic/messages/chatId').subscribe(msg => {
    console.log('Nuovo messaggio:', msg);
  });

  // Subscribe a notifiche personali
  this.websocketService.subscribe(`/user/${userId}/queue/notifications`);
});
```

### Invio Messaggio

```typescript
this.websocketService.send('/app/chat.sendMessage', {
  chatId: 'chat-uuid',
  content: 'Ciao!',
  sender: currentUser
});
```

---

## 📋 Componenti Principali

### ChatListComponent
- Visualizza lista chat
- Filtraggio e ricerca
- Selezionamento chat
- Input: `chats: Chat[]`
- Output: `chatSelected: EventEmitter<Chat>`

### MessageListComponent
- Visualizza messaggi della chat
- Scroll infinito
- Timestamp e sender
- Input: `messages: Message[]`
- Output: `messageDeleted: EventEmitter<string>`

### MessageInputComponent
- Input testuali con Ctrl+Enter per inviare
- Upload file
- Emoji picker
- Output: `messageSent: EventEmitter<MessageDto>`

### UserProfileComponent
- Visualizzazione profilo
- Modifica avatar e bio
- Lista contatti
- Input: `user: User`
- Output: `profileUpdated: EventEmitter<User>`

---

## 🎨 Stili

- **Bootstrap 5** - Grid e componenti
- **SCSS** - Preprocessore CSS con variabili
- **FontAwesome 6** - Icone SVG
- **Responsive Design** - Mobile-first approach

### Variabili SCSS personalizzate (`src/styles.scss`):
```scss
$primary-color: #25d366;    // Green WhatsApp
$secondary-color: #075e54;  // Dark green
$accent-color: #ff6b6b;     // Red
$text-dark: #222;
$text-light: #999;
```

---

## 🧪 Test

```bash
# Test unitari
ng test

# Test E2E
ng e2e

# Con coverage
ng test --code-coverage
```

---

## 📚 Documentazione API Auto-Generata

L'API client è generata automaticamente da OpenAPI spec del backend:

```bash
# Rigenerare client da OpenAPI spec
ng-openapi-gen
```

I modelli e servizi generati si trovano in `src/app/openapi/`

---

## 🐛 Troubleshooting

| Problema | Soluzione |
|----------|-----------|
| CORS errors | Verifica CORS nel backend `application.yml` |
| Token scaduto | AuthInterceptor refresh automatico, verifica Keycloak |
| WebSocket disconnette | Aumenta heartbeat in WebSocketService |
| Build fallisce | `npm install`, pulisci cache `ng cache clean` |
| Stili non caricano | Verifica percorsi in `angular.json`, pulisci `dist/` |

---

## 🚀 Deployment

### Static Hosting (Netlify, Vercel, GitHub Pages)

```bash
ng build --configuration production
# Carica contenuto di dist/ verso il provider
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

```json
{
  "@angular/common": "^19.0.0",
  "@angular/core": "^19.0.0",
  "@angular/forms": "^19.0.0",
  "@angular/router": "^19.0.0",
  "keycloak-js": "^26.0.0",
  "sockjs-client": "^1.6.1",
  "stompjs": "^2.3.3",
  "bootstrap": "^5.3.0",
  "@fortawesome/fontawesome-free": "^6.4.0",
  "rxjs": "^7.8.0"
}
```

Vedi `package.json` per elenco completo.
