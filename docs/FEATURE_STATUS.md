# Stato delle funzionalità

Stati: SPECIFICATA, IN_SVILUPPO, IMPLEMENTATA, TESTATA_CON_DATI_SIMULATI, TESTATA_CON_SERVIZIO_REALE, PRONTA_PER_PRODUZIONE, BLOCCATA_DA_DIPENDENZA.

## Release 1 — Fondamenta

| Funzionalità | Componente | Stato | Note |
| --- | --- | --- | --- |
| Repository, struttura, documenti di registro | tutti | IN_SVILUPPO | |
| Database e migrazioni Flyway | hub, edge | SPECIFICATA | Postgres 17 locale pronto |
| Contratti Hub↔Edge (comandi firmati) | contracts | SPECIFICATA | |
| Autenticazione, TOTP, ruoli e permessi | hub | SPECIFICATA | |
| Canale Hub↔Edge (WebSocket in uscita) | hub, edge | SPECIFICATA | |
| Job engine e scheduler | edge | SPECIFICATA | |
| AI gateway (Ollama, FreeLLMAPI) | edge | SPECIFICATA | |
| Servizio percorsi NAS | edge | SPECIFICATA | |
| Web: login, dashboard, PWA | fe | SPECIFICATA | |
| Script di installazione, avvio, arresto | infra, scripts | SPECIFICATA | |
