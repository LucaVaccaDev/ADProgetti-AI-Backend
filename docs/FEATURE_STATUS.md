# Stato delle funzionalità

Stati: SPECIFICATA, IN_SVILUPPO, IMPLEMENTATA, TESTATA_CON_DATI_SIMULATI, TESTATA_CON_SERVIZIO_REALE, PRONTA_PER_PRODUZIONE, BLOCCATA_DA_DIPENDENZA.

## Release 1 — Fondamenta

| Funzionalità | Componente | Stato | Note |
| --- | --- | --- | --- |
| Repository, struttura, documenti di registro | tutti | IN_SVILUPPO | |
| Database e migrazioni Flyway | hub, edge | TESTATA_CON_SERVIZIO_REALE | Postgres 17 reale (Testcontainers e locale); Edge con pgvector |
| Contratti Hub↔Edge (comandi firmati Ed25519) | contracts | IMPLEMENTATA | Testata con dati simulati (8 test unitari) |
| Autenticazione (login, refresh con rotazione, logout), ruoli e permessi, gestione utenti, audit | hub | TESTATA_CON_SERVIZIO_REALE | 22 test con Postgres reale. **TOTP non implementato (D-10)** |
| TOTP | hub | BLOCCATA_DA_DIPENDENZA | Sospeso su richiesta del committente (D-10); obbligatorio prima dell'esposizione su Internet |
| Canale Hub↔Edge — lato Hub (WebSocket, credenziale Edge, comandi firmati, outbox/inbox, proiezioni) | hub | TESTATA_CON_SERVIZIO_REALE | Postgres reale e client WebSocket reale (finto Edge in test); 22 test; prova con l'Edge vero il 05/11 |
| Canale Hub↔Edge — lato Edge (client in uscita, verifica comandi, policy, inbox/outbox, heartbeat, portachiavi) | edge | TESTATA_CON_SERVIZIO_REALE | 23 test con Postgres+pgvector e finto Hub; prova con l'Hub vero il 05/11. Comandi `ai.*` ancora senza esecutore (rifiutati UNSUPPORTED) |
| Job engine e scheduler | edge | SPECIFICATA | |
| AI gateway (Ollama, FreeLLMAPI) | edge | SPECIFICATA | |
| Servizio percorsi NAS | edge | SPECIFICATA | |
| Web: login, sessione, gestione utenti, PWA | fe | TESTATA_CON_DATI_SIMULATI | Test Vitest con HTTP simulato; non ancora verificato in un browser |
| Web: grafica Bento, menu laterale, intestazioni, Panoramica (struttura, calendario mensile) | fe | TESTATA_CON_DATI_SIMULATI | 18 test Vitest in totale. Riquadri senza dati dal Hub; non ancora verificata a vista in un browser |
| Web: Segreteria, Gare, BIM, Foto, Ricerca e chat, Calendario, Commesse, Amministrazione (pagine) | fe | IN_SVILUPPO | Solo struttura e stati vuoti; contenuti dei capitoli 4–11 di MENU_E_PAGINE da realizzare uno per volta |
| Stato sistema (`/api/system/status`) | hub | TESTATA_CON_SERVIZIO_REALE | Segue la connessione e l'heartbeat reali; senza Edge resta "non collegato" |
| Script di installazione, avvio, arresto | infra, scripts | SPECIFICATA | |
| Connettore IMAP Aruba (4 ordinarie + 2 PEC, PEC sola lettura, UID per cartella, IDLE) | edge | SPECIFICATA | Allegato D / D-11; sostituisce gli agenti Outlook. Prerequisiti D.3 da verificare col committente |
| Import storico email (IMAP + PST/OLM, marcato STORICO, riprendibile) | edge | SPECIFICATA | Allegato D.2; verificare spazio Mac/NAS e elenco PST/OLM |
| Modulo Gare — decisioni dettagliate (indice probabilità, criticità, requisiti, AI commissario, pacchetto offerta, firme, post-presentazione) | hub, edge, fe | SPECIFICATA | Allegato E / D-12; Release 4 |
| Profilo AI locale `test-24GB` e benchmark modelli | edge | SPECIFICATA | T-19 |
