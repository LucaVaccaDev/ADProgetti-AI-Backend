# Stato dei lavori e ripartenza

Aggiornato l'11/11/2026 (sera). Per riprendere: leggere questo file, poi `CLAUDE.md`, `docs/FEATURE_STATUS.md` e `docs/DECISION_LOG.md` (ultime voci T-40…T-44).

## Ripartenza del 12/11/2026 — gare e requisiti (D-23…D-29)

**Fatto l'11/11:** archivio referenze (import Excel, approvazione, finestre 3/5/10 anni), lettura dei disciplinari caricati in `/Volumes/AI/BANDI PROVVISORI` (file sciolti, abbinati per numero EC-xxxxxx o titolo; `.p7m` aperti; cancellati dopo la lettura; AI esterna solo per documenti pubblici). Ultimo commit: `e944d8f`. Edge riavviato con profilo `ssdtest`.

**Risultati letti:** EC-573910 (16° Stormo): importo € 598.011,83, OEPV, scadenza nel documento 06/11 contro 12/11 nel DB (da decidere quale vale); requisiti vaghi e non verificati. EC-572557 (Acqua Novara): € 3.000.000, OEPV, 9 servizi, 13 figure, 18 documenti, quasi tutto verificato; scadenza non trovata. Naturno (EC-572701) incompleta: da ricaricare (con il bando) per provare la rilettura dimezzata.

**Da fare domani:** (1) approvare le referenze sulla pagina Referenze (ora 26 da approvare, 3 da verificare, 0 approvate); (2) comando `tenders.compare` sull'Edge (servizi per ID opera/anni, fatturato provvisorio, personale da adprogetti.it, mai partner/RTP); (3) sezione Requisiti nella scheda gara con citazione e pagina, pulsante «Carica»; (4) test sulle due gare e aggiornamento di FEATURE_STATUS e DECISION_LOG. Scelte da confermare: scadenza di EC-573910, e se partire con le referenze non ancora approvate.

**Lavori a mano:** i lavori `TENDER_DOCS_READ` si lanciano da Amministrazione → Lavori, oppure inserendo una riga nella tabella `job` dell'Edge.

## Capitolato v1.2 (11/11/2026)

Recepito in D-13. **Grafica v1.2 realizzata nel frontend (D-14, commit `86b8e53`, `c536658`, `f3b93fe`, `96ce866`)**: token e Inter, libreria UI, barra laterale trasparente, Panoramica, sezioni, Amministrazione, login. Test: 77 Vitest verdi. Controllata a vista dal committente (Panoramica e barra laterale). Restano: pagine delle schermate 02–10 con i loro moduli (Gare con anello di fattibilità, Segreteria con anteprima protocollo, Calendario, Commesse, BIM, Foto, Chat), rifinitura di Amministrazione secondo la schermata 09, scadenze dalle email (F.4), profilo «produzione-64GB» (F.5).

**Postgres:** la 5432 è occupata dal Postgres dell'utenza `lucavacca`; il nostro cluster `~/pgdata` gira sulla **5433** (`pg_ctl -D ~/pgdata -o "-p 5433" start`) e il `.env` punta lì.

## Raccolta gare da TED (11/11/2026, D-17)

Fatto: connettore TED sull'Edge (job `TENDERS_SYNC`, 06:30 e a mano), proiezione `tenders.list`, `GET /api/tenders` sull'Hub, pagina Gare collegata con pulsante «Aggiorna da TED» (amministratore). Test verdi (Edge, Hub, 108 Vitest). **Da fare per vederle:** riavviare Hub ed Edge (migrazione V6), accedere con un utente con ruolo Responsabile gare o Collaboratore, premere «Aggiorna da TED» come amministratore (o attendere le 06:30). Poi: seconda fonte ANAC open data (sotto soglia), analisi documenti e fattibilità.

## Ripartenza dall'11/11/2026

**Fatto il 10/11:** canale Hub↔Edge funzionante (T-40); tutto sull'utenza `aihub`, Postgres in `~/pgdata`; prova NAS in sola lettura sull'SSD (T-41); `nas.list` fino alla pagina `/admin/nas` (T-42); FreeLLMAPI nel gateway AI, chiave nel portachiavi, catena TESTO con 46 modelli (T-43); indice documentale PDF con PDFBox, `docs.search` e pagina `/admin/docs` (T-44, commit `20e2674`). Test: 286 backend, 39 frontend, tutti verdi.

**Servizi (avvio manuale, non ripartono da soli):** Colima, Postgres (`~/pgdata`), Ollama, FreeLLMAPI (`npm run dev` in `~/tools/freellmapi`, Node 24 per primo nel `PATH`), Hub `bash scripts/start-hub.sh`, Edge `bash scripts/start-edge.sh --spring.profiles.active=ssdtest`, frontend `npm start` in `fe/`. Log in `~/logs/`. Segreti in `.env` (non in git) e nel portachiavi.

**Da fare (stamattina non ancora fatto):**
1. Avviare i servizi, aprire `/admin/docs`, premere «Aggiorna indice» (200 file per giro, solo `GARE 2025`) finché è «Completata»; provare qualche ricerca; tarare `edge.docs.min-similarity`.
2. Premere «Prova FreeLLMAPI» in Amministrazione > Modelli AI (la catena TESTO ora ha modelli; con curl funzionava).

**Aperti:** `.p7m` (841 in GARE 2025, serve BouncyCastle); OCR per circa il 6% di PDF scansionati (Tesseract non installato); ripiego AI locale senza modello ANALYSIS (T-38, SSD lento); catena IMMAGINI con soli modelli testo; 37 vulnerabilità npm in FreeLLMAPI da rivedere; `infra/` e launchd per l'avvio automatico; `npm audit`/Dependency-Check (T-10); NAS vero (utente di sola lettura, nomi condivisioni, cartella protocolli: `docs/NAS.md`); Release 2 (IMAP Aruba, protocollo: credenziali e elenco PST/OLM); il repository non ha un remote.

## Dove siamo (Release 1 — Fondamenta)

**Fatto**
- Repository, contratti Hub↔Edge (comandi firmati Ed25519), Hub (database, autenticazione, ruoli, utenti, audit; TOTP escluso dal progetto, D-10).
- Web: login, gestione utenti, PWA, grafica Bento, menu laterale in vetro, Panoramica, pagine di sezione con stati vuoti (T-15…T-18). Specifiche in `docs/GRAFICA.md` e `docs/MENU_E_PAGINE.md`.
- Build e 18 test Vitest verdi. L'interfaccia è stata controllata a vista dal committente (ok); **tutti i dati sono ancora statici** e vanno collegati.

**Capitolato v1.1 recepito (05/11)** — `docs/capitolato/Capitolato_Progetto_Zio_Pino_v1.1.md`: posta via **IMAP diretto dall'Edge** (Allegato D, D-11; niente agenti Outlook), Allegato E sul modulo Gare (D-12), profilo AI `test-24GB` (T-19). Aggiornati CLAUDE.md, DECISION_LOG, FEATURE_STATUS, MENU_E_PAGINE e le etichette del frontend (T-20).

**Fatto il 05/11:** capitolato v1.1 recepito; contratti; canale Hub↔Edge (lato Hub e lato Edge) provato con Hub ed Edge veri; Ollama installato con Qwen3 8B e bge-m3 sull'SSD. Dettagli in `docs/ARCHITETTURA_CANALE.md`.

**Fatto anche (passo 4):** AI gateway locale, comandi `ai.*`, registro d'uso, benchmark (`docs/AI_LOCALE.md`). **Fatto anche (05/11 sera): job engine e scheduler** sull'Edge con comandi `jobs.*`, proiezione `jobs.summary` e pagina web «Lavori» (T-32…T-34). **Bloccato (T-38):** l'SSD esterno è troppo lento per caricare i modelli grandi; prima verificare porta/cavo USB, poi rilanciare. **Da fare:** terminato il download di `gpt-oss:20b`, `gemma4:26b-a4b-it-q4_K_M` e `qwen3.6:27b-q4_K_M` (SSD), lanciare `python3 -I scripts/benchmark-ai.py <modelli> --out docs/benchmark`, scegliere il modello principale e assegnarlo. Poi: servizio NAS, collegare i dati reali al web (stato sistema e modelli AI nella pagina Amministrazione).

**Piano originario (superato):** creare il modulo **Edge** (`apps/edge`, sul Mac Mini, utenza dedicata) e il **canale Hub↔Edge** (WebSocket in uscita, comandi firmati, outbox/inbox). Il piano (moduli, file, migrazioni, endpoint, test) va mostrato e confermato prima di scrivere codice (CLAUDE.md §6).

**Poi, ancora da fare nella Release 1:** collegare i dati reali al web, job engine e scheduler, servizio percorsi NAS, AI gateway (Ollama + FreeLLMAPI, gruppi TESTO e IMMAGINI), script di avvio e installazione in `infra/`, stato di sistema reale.

**Alternative indipendenti dall'Edge** (se si vuole un risultato visibile prima): calendario sull'Hub, Dependency-Check e `npm audit` (T-10), `infra/` con nginx e CSP.

## Ambiente (Mac Mini M4 Pro, 24 GB)

- **Utenza Edge (`aihub`)**: JDK 21, Maven e Node 24 in `~/tools` (`source scripts/env.sh`). Ollama in `~/tools/ollama` come servizio launchd su 127.0.0.1:11434, modelli sull'SSD `/Volumes/AI/ollama/models` (T-21, T-22). Colima per Testcontainers va avviato (`colima start`) prima dei test dell'Hub.

- Lo sviluppo è passato a un'**utenza dedicata all'Edge**. Cose per utente da rifare lì: configurazione git e Claude Code, estensioni VS Code, accessi, `~/tools` (Node 24 LTS, Maven 3.9: vedi `scripts/env.sh`), Colima per Testcontainers.
- Homebrew nativo in `/opt/homebrew` (Postgres 17 + pgvector, Colima). Il `brew` in `/usr/local` è la versione Intel e non funziona.
- **Node:** in `PATH` può esserci un node Intel in `/usr/local/bin`: con i moduli arm64 di `node_modules` fa fallire `ng serve`. Usare Node 24 da `scripts/env.sh` o `/opt/homebrew/bin/node`.
- Database locali `zio_hub` e `zio_edge` con utenti separati (T-03). Il file `.env` (permessi 600, non in git) contiene i segreti di sviluppo e **non viaggia con il repository**: copiarlo o ricrearlo da `.env.example`.
- `scripts/start-hub.sh` non è eseguibile: lanciarlo con `bash scripts/start-hub.sh`. Frontend: `npm start` in `fe/` (proxy verso 8080).
- `docs/materiali/` è escluso da git (T-04): file del committente da copiare a mano.
- Il repository **non ha un remote**: vive solo nella cartella dell'utenza `lucavacca`.
