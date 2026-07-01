# WacChat Grafana dashboards

`wacchat-overview.json` is auto-provisioned (request rate, p95 latency, error rate, JVM heap per service).

Community dashboards are not vendored here — import them manually from Grafana.com after the stack is up (Dashboards → New → Import), using the Prometheus datasource (`prometheus-uid`):

- **4701** — JVM (Micrometer)
- **14205** or **19004** — Spring Boot 3 / Micrometer
