# Stato dei lavori e ripartenza

Aggiornato il 05/11/2026 (sera). Per riprendere: leggere questo file, poi `CLAUDE.md`, `docs/FEATURE_STATUS.md` e `docs/DECISION_LOG.md`.

## Dove siamo (Release 1 — Fondamenta)

**Fatto**
- Repository, contratti Hub↔Edge (comandi firmati Ed25519), Hub (database, autenticazione, ruoli, utenti, audit; senza TOTP, D-10).
- Web: login, gestione utenti, PWA, grafica Bento, menu laterale in vetro, Panoramica, pagine di sezione con stati vuoti (T-15…T-18). Specifiche in `docs/GRAFICA.md` e `docs/MENU_E_PAGINE.md`.
- Build e 18 test Vitest verdi. L'interfaccia è stata controllata a vista dal committente (ok); **tutti i dati sono ancora statici** e vanno collegati.

**Capitolato v1.1 recepito (05/11)** — `docs/capitolato/Capitolato_Progetto_Zio_Pino_v1.1.md`: posta via **IMAP diretto dall'Edge** (Allegato D, D-11; niente agenti Outlook), Allegato E sul modulo Gare (D-12), profilo AI `test-24GB` (T-19). Aggiornati CLAUDE.md, DECISION_LOG, FEATURE_STATUS, MENU_E_PAGINE e le etichette del frontend (T-20).

**Fatto il 05/11:** capitolato v1.1 recepito; contratti; canale Hub↔Edge (lato Hub e lato Edge) provato con Hub ed Edge veri; Ollama installato con Qwen3 8B e bge-m3 sull'SSD. Dettagli in `docs/ARCHITETTURA_CANALE.md`.

**Fatto anche (passo 4):** AI gateway locale, comandi `ai.*`, registro d'uso, benchmark (`docs/AI_LOCALE.md`). **Da fare:** terminato il download di `gpt-oss:20b`, `gemma4:26b-a4b-it-q4_K_M` e `qwen3.6:27b-q4_K_M` (SSD), lanciare `python3 -I scripts/benchmark-ai.py <modelli> --out docs/benchmark`, scegliere il modello principale e assegnarlo. Poi: job engine e scheduler, servizio NAS, collegare i dati reali al web (stato sistema e modelli AI nella pagina Amministrazione).

**Piano originario (superato):** creare il modulo **Edge** (`apps/edge`, sul Mac Mini, utenza dedicata) e il **canale Hub↔Edge** (WebSocket in uscita, comandi firmati, outbox/inbox). Il piano (moduli, file, migrazioni, endpoint, test) va mostrato e confermato prima di scrivere codice (CLAUDE.md §6).

**Poi, ancora da fare nella Release 1:** collegare i dati reali al web, job engine e scheduler, servizio percorsi NAS, AI gateway (Ollama + FreeLLMAPI, gruppi TESTO e IMMAGINI), script di avvio e installazione in `infra/`, stato di sistema reale.

**Alternative indipendenti dall'Edge** (se si vuole un risultato visibile prima): TOTP (obbligatorio prima di esporre l'Hub su Internet), calendario sull'Hub, Dependency-Check e `npm audit` (T-10), `infra/` con nginx e CSP.

## Ambiente (Mac Mini M4 Pro, 24 GB)

- **Utenza Edge (`aihub`)**: JDK 21, Maven e Node 24 in `~/tools` (`source scripts/env.sh`). Ollama in `~/tools/ollama` come servizio launchd su 127.0.0.1:11434, modelli sull'SSD `/Volumes/AI/ollama/models` (T-21, T-22). Colima per Testcontainers va avviato (`colima start`) prima dei test dell'Hub.

- Lo sviluppo è passato a un'**utenza dedicata all'Edge**. Cose per utente da rifare lì: configurazione git e Claude Code, estensioni VS Code, accessi, `~/tools` (Node 24 LTS, Maven 3.9: vedi `scripts/env.sh`), Colima per Testcontainers.
- Homebrew nativo in `/opt/homebrew` (Postgres 17 + pgvector, Colima). Il `brew` in `/usr/local` è la versione Intel e non funziona.
- **Node:** in `PATH` può esserci un node Intel in `/usr/local/bin`: con i moduli arm64 di `node_modules` fa fallire `ng serve`. Usare Node 24 da `scripts/env.sh` o `/opt/homebrew/bin/node`.
- Database locali `zio_hub` e `zio_edge` con utenti separati (T-03). Il file `.env` (permessi 600, non in git) contiene i segreti di sviluppo e **non viaggia con il repository**: copiarlo o ricrearlo da `.env.example`.
- `scripts/start-hub.sh` non è eseguibile: lanciarlo con `bash scripts/start-hub.sh`. Frontend: `npm start` in `fe/` (proxy verso 8080).
- `docs/materiali/` è escluso da git (T-04): file del committente da copiare a mano.
- Il repository **non ha un remote**: vive solo nella cartella dell'utenza `lucavacca`.
