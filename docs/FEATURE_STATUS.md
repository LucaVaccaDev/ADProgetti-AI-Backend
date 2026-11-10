# Stato delle funzionalità

Stati: SPECIFICATA, IN_SVILUPPO, IMPLEMENTATA, TESTATA_CON_DATI_SIMULATI, TESTATA_CON_SERVIZIO_REALE, PRONTA_PER_PRODUZIONE, BLOCCATA_DA_DIPENDENZA.

## Release 1 — Fondamenta

| Funzionalità | Componente | Stato | Note |
| --- | --- | --- | --- |
| Repository, struttura, documenti di registro | tutti | IN_SVILUPPO | |
| Database e migrazioni Flyway | hub, edge | TESTATA_CON_SERVIZIO_REALE | Postgres 17 reale (Testcontainers e locale); Edge con pgvector |
| Contratti Hub↔Edge (comandi firmati Ed25519) | contracts | IMPLEMENTATA | Testata con dati simulati (8 test unitari) |
| Autenticazione (login, refresh con rotazione, logout), ruoli e permessi, gestione utenti, audit | hub | TESTATA_CON_SERVIZIO_REALE | 22 test con Postgres reale. **TOTP escluso dal progetto (D-10)** |
| Canale Hub↔Edge — lato Hub (WebSocket, credenziale Edge, comandi firmati, outbox/inbox, proiezioni) | hub | TESTATA_CON_SERVIZIO_REALE | Postgres reale e client WebSocket reale (finto Edge in test); 22 test; prova con l'Edge vero il 05/11 |
| Canale Hub↔Edge — lato Edge (client in uscita, verifica comandi, policy, inbox/outbox, heartbeat, portachiavi) | edge | TESTATA_CON_SERVIZIO_REALE | 23 test con Postgres+pgvector e finto Hub; prova con l'Hub vero il 05/11. Comandi `ai.*` ancora senza esecutore (rifiutati UNSUPPORTED) |
| Web: stato del sistema (Panoramica) e pagina Amministrazione › Modelli AI (elenco, assegnazione, test rapido, ripristino) | web | TESTATA_CON_DATI_SIMULATI | Collegata alle API reali dell'Hub (`/api/system/status`, `/api/ai/*`, `/api/commands/{id}`); 23 test Vitest con risposte simulate. Prova a vista con Hub+Edge veri ancora da fare |
| Job engine e scheduler (Edge) | edge | TESTATA_CON_SERVIZIO_REALE | Coda su Postgres reale, 3 worker, idempotenza, retry con backoff, lock per risorsa, annullamento, recupero dopo riavvio, priorità, scheduler persistente con recupero slot e ora legale; 27 test. Job reale: SYSTEM_SELFCHECK. La pianificazione 14:30/18:30 è spenta finché non esiste il job PROTOCOL_RUN (Release 2). Comandi `jobs.run`/`jobs.cancel` e proiezione `jobs.summary` fino all'Hub (`/api/jobs`): testati con Hub finto/Edge finto. Pagina web Amministrazione › Lavori (coda, pianificazioni, avvio e annullamento) e riga «Lavori» in Panoramica; provata con Hub ed Edge veri il 05/11 (avvio manuale di SYSTEM_SELFCHECK dall'API) |
| Servizio percorsi NAS (modalità lettura/protocolli/gare, anti-traversal, scrittura atomica verificata, controllo di disponibilità) | edge | TESTATA_CON_DATI_SIMULATI | Provato su cartella locale di prova (24 test: traversal, link simbolici, modalità, collisioni, concorrenza, hash, gare storiche, audit). NAS vero (192.168.1.57) non montato su questo Mac: prova reale ancora da fare. Riga NAS in Panoramica reale. Vedi `docs/NAS.md` Sola lettura provata su SSD esterno `/Volumes/AI` (7 test, profilo `ssdtest`; non sul NAS vero, scritture non provate su servizio reale). |
| Esplora NAS in sola lettura (`nas.list`, pagina `/admin/nas`) | edge, hub, web | IMPLEMENTATA | Test automatici verdi (NAS finto); prova a vista sull'SSD con profilo `ssdtest` da fare. Il risultato è cancellato dall'Hub dopo 2 minuti (T-42). Non provata sul NAS vero. |
| AI gateway locale (Ollama): coda a due corsie, output validato, registro d'uso, comandi `ai.models.list`/`ai.model.test`/`ai.model.assign`, proiezioni `ai.models` e `ai.usage` | edge, hub | TESTATA_CON_SERVIZIO_REALE | Test con finto Ollama + test `real-ai` con Ollama, Qwen3 8B e bge-m3 reali (05/11). Modelli grandi: scaricamento in corso, nessun modello assegnato per ANALYSIS/DRAFTING/IMAGES |
| AI gateway: provider FreeLLMAPI (ricerca generale; gateway, filtro A.2, limite per utente, ripiego locale, prova da Amministrazione) | edge, hub, web | TESTATA_CON_DATI_SIMULATI | Provato con un finto FreeLLMAPI (17 test Edge, 1 Hub, 3 web). **Non ancora provato con il servizio vero**: manca la chiave `freellmapi-key` nel portachiavi (`bash scripts/edge-set-secret.sh freellmapi-key`). Il pulsante «Ricerca generale» per gli utenti arriverà con la chat (Alfonzo). |
| Indice documentale PDF (estrazione PDFBox, passaggi, embedding, ricerca ibrida, `docs.search`, pagina «Indice documenti») | edge, hub, web | TESTATA_CON_DATI_SIMULATI | Test su PDF generati (Edge 31+, Hub 2, web 5) e prova di estrazione su un campione reale di 120 PDF dell'SSD (112 con testo, 7 scansioni, 1 con troppo testo). Indice di GARE 2025 non ancora eseguito per intero; `min-similarity` da tarare; mancano `.p7m` e OCR. |
| AI gateway: gruppo IMMAGINI di FreeLLMAPI (fotoritocco) | edge | BLOCCATA_DA_DIPENDENZA | Serve il gruppo IMMAGINI in FreeLLMAPI e la verifica dei modelli con immagini nelle quote gratuite (A.5); nessun codice immagini finché non c'è il modulo foto. |
| Benchmark modelli AI | scripts | TESTATA_CON_DATI_SIMULATI | Dati sintetici; da ripetere sul campione reale anonimizzato |
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
