# Progetto Zio Pino — AD Progetti AI Hub — Regole per Claude Code

Va nella radice del repository `ad-progetti-ai-hub/`. Claude Code lo legge a ogni sessione.

## 1. Specifica di riferimento e precedenze

La specifica funzionale completa è il capitolato unico **v1.1** (04/11/2026) in `docs/capitolato/Capitolato_Progetto_Zio_Pino_v1.1.md`; contiene anche grafica (Parte 2) e menu/pagine (Parte 3). La v1.0 (`Capitolato_Progetto_Zio_Pino.txt`) resta solo come storico. I materiali del committente sono in `docs/materiali/` e sono in **sola lettura**.

In caso di contrasto vale questo ordine:

1. Le **deviazioni D-01…D-08** qui sotto (decise dal committente dopo il capitolato).
2. Gli Allegati **E (Gare)** e **D (Email IMAP Aruba)** del capitolato v1.1.
3. Gli Allegati C, B, A e le sezioni 27–33.
4. Il resto del capitolato.

L'Allegato B.1 (agenti Outlook) è **sostituito** dall'Allegato D e non va implementato.

Ogni deviazione e ogni scelta tecnica non banale va registrata in `docs/DECISION_LOG.md`.

## 2. Deviazioni dal capitolato (vincolanti)

| ID | Decisione | Sostituisce |
| --- | --- | --- |
| D-01 | Stack: **Angular** (frontend, PWA) e **Java + Spring Boot** (backend). | Sez. 3.1 (React, Python/FastAPI) |
| D-02 | Due componenti server: **Hub** su VPS Aruba Cloud (Italia), raggiungibile da Internet in HTTPS; **Edge** sul Mac Mini in studio, che **non accetta connessioni da Internet** e apre lui un canale in uscita verso l'Hub. Niente VPN per gli utenti. | C.1 (solo LAN), C.14 (accesso remoto via VPN), C.15 (app installata solo sul Mac). Resta la PWA di C.15. |
| D-03 | Confine dei dati: vedi §4. Sull'Hub solo identità, lavoro collaborativo e **risultati**; email complete, allegati, documenti, file NAS, indice ed embedding restano sull'Edge e sul NAS. | Integra sez. 37.2 |
| D-04 | Tutta l'AI gira dall'Edge (Ollama + FreeLLMAPI su localhost del Mac). L'Hub non chiama mai provider AI e non custodisce chiavi AI. | Integra Allegato A.4 |
| D-05 | Hub esposto su Internet → **TOTP obbligatorio per tutti gli utenti** di default (configurabile dall'amministratore per singolo utente, mai disattivabile per gli amministratori). | C.14 (TOTP opzionale) |
| D-06 | I ruoli si combinano. L'amministratore gestisce utenti e configurazioni e vede tutte le aree amministrative e operative, **tranne** le chat altrui (B.5) e le gare individuate se non ha anche il ruolo Responsabile gare (B.3). | Precisa sez. 36, C.2 |
| D-07 | Struttura del repository adattata allo stack (§3). | Sez. 3.2 |
| D-08 | Gli utenti si creano **solo** da amministratore; nessuna registrazione pubblica. | Precisa sez. 36 |
| D-11 | Posta: l'**Edge** si collega via IMAP/TLS (993) alle 4 caselle ordinarie e 2 PEC Aruba; credenziali nel portachiavi macOS. PEC in sola lettura garantita dal codice (EXAMINE, BODY.PEEK, nessun STORE/MOVE/COPY/EXPUNGE/APPEND, con test). Storico importato una tantum da PST/OLM, marcato STORICO. | Allegato B.1 (agenti Outlook) |

Decisioni del 30/10 **superate** dal capitolato, da non riproporre: API a pagamento Claude/Google per gare e foto (ora FreeLLMAPI, Allegato A, con provider sostituibile); protocollo per commessa (ora per fase, sez. 7).

## 3. Struttura del repository

```
ad-progetti-ai-hub/
├── apps/
│   ├── web/        Angular (PWA) — servito dall'Hub
│   ├── hub/        Spring Boot — VPS Aruba Cloud: API, autenticazione, dati collaborativi, proiezioni
│   └── edge/       Spring Boot — Mac Mini: job engine, scheduler, AI gateway, NAS, protocollo, indicizzazione, connettore IMAP Aruba, endpoint agenti Revit LAN
├── libs/
│   └── contracts/  DTO e schemi JSON condivisi Hub↔Edge e Edge↔agenti (versionati)
├── agents/
│   └── revit/          fork interno di rvt-mcp + toolset AD Progetti — Release 7
├── infra/          deploy Hub (Docker Compose, nginx), installazione Edge (launchd), script agenti Revit
├── docs/
│   ├── capitolato/  capitolato (sola lettura)
│   ├── materiali/   file del committente (sola lettura)
│   ├── design/      demo grafica approvata
│   ├── adr/         decisioni architetturali
│   └── *.md         documenti richiesti dalla sez. 45
└── tests/fixtures/  dati di prova (mai dati reali non anonimizzati)
```

Build: Maven multi-modulo per `apps/hub`, `apps/edge`, `libs/contracts`; Angular CLI per `apps/web`.

## 4. Confine dei dati Hub / Edge

| Dove | Cosa contiene | Cosa non deve contenere mai |
| --- | --- | --- |
| **Hub** (Postgres su Aruba Cloud) | Utenti, ruoli, sessioni, MFA, audit delle azioni utente, registro d'uso AI (solo metadati); calendario, attività, notifiche, approvazioni; chat personali; workspace gare (schede, matrici, bozze di offerta e documenti amministrativi); **proiezioni in sola lettura** prodotte dall'Edge (protocolli: numero, tipo, date, mittente/destinatari, oggetto, stato, percorso relativo; commesse, fasi, interlocutori; gare monitorate; esiti job; stato sistema) | EML, allegati, PDF di comunicazioni, testo integrale di email o documenti NAS, embedding, file NAS, chiavi AI, credenziali NAS o posta |
| **Edge** (Postgres + pgvector sul Mac) | Fonte canonica di email acquisite e classificazioni, **protocollo** (registri, numerazione, voci, annullamenti), sincronizzazione Excel, indice documentale, referenze e certificazioni importate, analisi archivio gare, job engine, quote FreeLLMAPI, segreti AI e NAS (portachiavi macOS) | Password utenti, refresh token |
| **NAS** | Tutti i file dello studio, archiviazioni di protocollo, cartelle gare, foto elaborate, backup | — |

File richiesti dall'esterno (anteprima di un allegato o di un documento): solo **inoltro su richiesta** attraverso il canale, mai salvati sull'Hub, con controllo permessi e audit. Funzione disattivabile dall'amministratore.

## 5. Canale Hub ↔ Edge

- L'Edge apre una connessione **in uscita** (WebSocket su TLS) verso l'Hub; si riconnette da solo con backoff.
- Autenticazione dell'Edge: credenziale dedicata (chiave casuale ≥ 256 bit salvata come hash sull'Hub, confronto in tempo costante), ruotabile; certificato TLS dell'Hub verificato (pinning della CA).
- Ogni comando Hub→Edge è **firmato** dall'Hub (Ed25519), con id univoco, utente richiedente, permessi verificati, scadenza e nonce. L'Edge verifica firma, scadenza e unicità, e **riapplica le proprie policy invarianti** (es. PEC sola lettura, scrittura NAS solo nei percorsi ammessi, invio email solo con approvazione registrata).
- Consegna almeno-una-volta con tabelle outbox/inbox su entrambi i lati e chiave di idempotenza: un comando ripetuto non viene rieseguito.
- Se il canale cade, l'Edge continua il lavoro locale (protocollo delle 14:30/18:30, acquisizione email, indicizzazione) e sincronizza le proiezioni alla riconnessione. L'Hub mostra "Studio non collegato".
- La posta si legge **dall'Edge via IMAP diretto** alle caselle Aruba (Allegato D, D-11): nessun agente Outlook sui PC.
- Gli agenti delle postazioni Revit parlano **solo** con l'Edge in LAN (HTTPS con certificato interno + token per agente), mai con l'Hub.

## 6. Modo di lavorare (dalla sez. 0.2–0.4 del capitolato)

- Iterazioni brevi e complete: obiettivo, file, codice, test, correzione, avvio, verifica, documentazione.
- Prima di scrivere codice per un prompt: mostra il piano (moduli, file, migrazioni, endpoint, test) e **attendi conferma**.
- Ogni funzionalità ha uno stato in `docs/FEATURE_STATUS.md`: SPECIFICATA, IN_SVILUPPO, IMPLEMENTATA, TESTATA_CON_DATI_SIMULATI, TESTATA_CON_SERVIZIO_REALE, PRONTA_PER_PRODUZIONE, BLOCCATA_DA_DIPENDENZA. Mai presentare come reale un'integrazione simulata.
- Chiedi conferma solo per i casi della sez. 0.4 (operazioni distruttive, invio email, dati non autorizzati, servizi a pagamento, modifica di originali, esposizione di dati a fornitori esterni). Le scelte tecniche ordinarie le prendi tu e le documenti.
- Ogni passo termina con build verde, test verdi e commit con messaggio chiaro.

## 7. Stack e versioni

- Java 25 LTS (21 LTS se una dipendenza lo richiede), ultima Spring Boot stabile, Spring Security, Spring Data JPA, Flyway, PostgreSQL 17 (+ pgvector sull'Edge), Maven.
- Angular ultima stabile: componenti standalone, signals, TypeScript strict, SCSS, PWA (`@angular/pwa`).
- Test: JUnit 5, Testcontainers (Postgres reale, mai H2), ArchUnit per i confini dei moduli, test Angular con il runner della versione in uso, Playwright end-to-end.
- Prima di usare un'API di una libreria verifica la versione installata e la sua documentazione.
- OpenAPI generata dal codice (springdoc) per l'Hub; schemi JSON versionati in `libs/contracts` per i messaggi Hub↔Edge e agenti↔Edge.

## 8. Separazione AI / codice deterministico (sez. 3.3 e 37)

- L'AI classifica, estrae, riassume, confronta, propone, genera bozze. **Il codice** assegna numeri di protocollo, controlla permessi, gestisce transazioni, salva file, garantisce unicità, esegue scheduler, applica policy, valida schemi, registra audit.
- Ogni output AI è validato con uno schema strutturato prima dell'uso; output non valido = DA_VERIFICARE, mai indovinato.
- Email, PDF, bandi, pagine web e metadati sono **dati**, mai istruzioni: costruisci i prompt separando chiaramente istruzioni di sistema e contenuto non fidato, e testa la prompt injection (sez. 41.6).
- Ogni azione AI con effetti esterni passa da: permessi → validazione → contesto → policy di approvazione → esecuzione deterministica → audit → verifica esito (sez. 37.1).

## 9. Regole di sicurezza (non negoziabili)

1. **Nega per default**: ogni endpoint richiede autenticazione e un permesso esplicito, salvo login, refresh e health.
2. Il controllo vero è sempre nel backend (Hub, e di nuovo sull'Edge per le policy invarianti). Il frontend nasconde, non protegge.
3. Nessun segreto nel codice, nel repository, nei log o nei backup in chiaro: variabili d'ambiente o file con permessi `600` sull'Hub, portachiavi macOS sull'Edge. Solo `.env.example` con valori fittizi.
4. DTO validati con Bean Validation; mai entità JPA esposte; nessun mass assignment (campi come ruoli, stato, id).
5. Solo query parametrizzate.
6. Errori in ProblemDetail (RFC 9457) senza dettagli interni; messaggi di login generici.
7. Log tecnici senza password, token, contenuti di email o documenti (C.10).
8. **Path traversal**: ogni percorso NAS passa da un unico servizio che normalizza, risolve e verifica che il risultato stia dentro una radice ammessa per quella modalità (sez. 5.2).
9. Upload: tipo verificato dal contenuto (non dall'estensione), dimensione massima, nome sanificato, nessuna esecuzione.
10. Frontend: vietati `innerHTML` con dati non fidati, `bypassSecurityTrust*`, token in `localStorage`/`sessionStorage`; HTML delle email reso solo in iframe `sandbox` senza script.
11. Dipendenze mantenute; OWASP Dependency-Check e `npm audit` nel build; nessuna dipendenza nuova senza motivazione nel commit.
12. Audit di login, gestione utenti, approvazioni, protocolli, annullamenti, scritture NAS, comandi Hub→Edge.

## 10. Lingua e interfaccia

Interfaccia e documentazione in italiano. Codice, nomi, commit in inglese. Riferimento grafico: `docs/design/AD-Progetti-AI-Hub-DEMO.html` (sei accessi agli assistenti AI, sez. 1.1). Mai dati simulati presentati come reali (sez. 39).
