# Stato dei lavori e ripartenza

Aggiornato il 09/11/2026 (sera). Per riprendere: leggere questo file, poi `CLAUDE.md`, `docs/FEATURE_STATUS.md` e `docs/DECISION_LOG.md`.

## Ripartenza dal 09/11/2026

**Decisioni di oggi:** TOTP escluso dal progetto (D-10, D-05 superata); FreeLLMAPI installato senza Docker (T-39).

**Servizi in esecuzione (avvio manuale, non ripartono da soli al riavvio):** Postgres 17 (utenza `lucavacca`), Ollama (11434), Hub (8080), Edge (8081), frontend `npm start` (4200), FreeLLMAPI `npm run dev` (server 3001, dashboard 5173, solo localhost). Log in `/private/tmp/claude-502/logs/`. Per riavviare: `bash scripts/start-hub.sh`, `bash scripts/start-edge.sh`, `npm start` in `fe/` con Node 24 per primo nel `PATH` (`$HOME/tools/node-v24.22.2-darwin-arm64/bin`; il Node 26 di Homebrew non va per FreeLLMAPI). `brew` non è utilizzabile da questa utenza.

**Problema aperto, da fare per primo:** l'Edge si avvia ma **non risulta collegato all'Hub** (nessuna riga di connessione nel log). Sospetto: credenziale dell'Edge e chiave pubblica dell'Hub mancanti nel portachiavi di questa utenza (`scripts/edge-set-secret.sh`). Da verificare nel codice come l'Edge le cerca. Può servire anche un utente amministratore (`scripts/create-admin.sh`).

**FreeLLMAPI:** installato in `~/tools/freellmapi`, nessun account né chiave ancora inseriti. Tocca al committente: creare l'account sulla dashboard (http://localhost:5173), creare le chiavi dei provider gratuiti (consigliati: Google Gemini, Groq, Mistral, OpenRouter; per le immagini SiliconFlow) e inserirle nella pagina Keys, senza incollarle in chat. Poi: gruppi TESTO/IMMAGINI, servizio launchd, collegamento all'`AiGateway`, chiave `freellmapi-…` nel portachiavi dell'Edge. Controllare i termini dei piani gratuiti sull'uso dei dati (Allegato A.3). Ci sono 37 vulnerabilità npm segnalate nel clone, da guardare prima della produzione.

**NAS:** non montato (in `/Volumes` solo `AI`). Servono: utente NAS in sola lettura, conferma dei nomi delle condivisioni, nome reale della cartella dei protocolli. Procedura in `docs/NAS.md`; poi prova dei test sul NAS vero.

**Ancora bloccato (T-38):** benchmark dei modelli grandi, per l'SSD esterno lento.

**Ordine proposto per domani:** 1) canale Hub↔Edge; 2) NAS; 3) FreeLLMAPI (chiavi e gateway); 4) `infra/` e script di installazione, `npm audit`/Dependency-Check; 5) Release 2 (IMAP Aruba, protocollo): servono credenziali IMAP e elenco PST/OLM. Piano completo delle Release 2–8 in capitolato sez. 43.

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
