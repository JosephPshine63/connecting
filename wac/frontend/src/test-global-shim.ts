// sockjs-client (used by the STOMP/WebSocket chat connection) references the Node.js
// `global` even in its browser build. Angular's production `application` builder (esbuild)
// polyfills this automatically, but the webpack-based Karma test builder does not.
(globalThis as unknown as { global: typeof globalThis }).global ??= globalThis;
