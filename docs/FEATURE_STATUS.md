# Stato delle funzionalità

Stati: SPECIFICATA, IN_SVILUPPO, IMPLEMENTATA, TESTATA_CON_DATI_SIMULATI, TESTATA_CON_SERVIZIO_REALE, PRONTA_PER_PRODUZIONE, BLOCCATA_DA_DIPENDENZA.

## Release 1 — Fondamenta

| Funzionalità | Componente | Stato | Note |
| --- | --- | --- | --- |
| Repository, struttura, documenti di registro | tutti | IN_SVILUPPO | |
| Database e migrazioni Flyway | hub | TESTATA_CON_SERVIZIO_REALE | Postgres 17 reale (Testcontainers e locale). Edge: SPECIFICATA |
| Contratti Hub↔Edge (comandi firmati Ed25519) | contracts | IMPLEMENTATA | Testata con dati simulati (8 test unitari) |
| Autenticazione (login, refresh con rotazione, logout), ruoli e permessi, gestione utenti, audit | hub | TESTATA_CON_SERVIZIO_REALE | 22 test con Postgres reale. **TOTP non implementato (D-10)** |
| TOTP | hub | BLOCCATA_DA_DIPENDENZA | Sospeso su richiesta del committente (D-10); obbligatorio prima dell'esposizione su Internet |
| Canale Hub↔Edge (WebSocket in uscita) | hub, edge | SPECIFICATA | |
| Job engine e scheduler | edge | SPECIFICATA | |
| AI gateway (Ollama, FreeLLMAPI) | edge | SPECIFICATA | |
| Servizio percorsi NAS | edge | SPECIFICATA | |
| Web: login, sessione, dashboard, gestione utenti, PWA | fe | TESTATA_CON_DATI_SIMULATI | 11 test Vitest con HTTP simulato; non ancora verificato in un browser |
| Stato sistema (`/api/system/status`) | hub | IMPLEMENTATA | Edge sempre "non collegato" finché non esiste il canale |
| Script di installazione, avvio, arresto | infra, scripts | SPECIFICATA | |
