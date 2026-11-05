# Canale Hub ↔ Edge

Aggiornato il 05/11/2026. Specifica: CLAUDE.md §5. Implementazione lato Hub: `be/hub/.../channel`.

## Connessione
- L'Edge apre una WebSocket **in uscita** verso `wss://<hub>/edge/ws`. L'Hub non apre mai connessioni verso l'Edge.
- Autenticazione nell'handshake con l'header `X-Edge-Key` (credenziale a 256 bit; sull'Hub solo l'hash SHA-256, confronto in tempo costante). Dopo 10 tentativi falliti in un minuto dallo stesso indirizzo: 429. Percorso fuori dalla catena JWT per scelta (l'Edge non è un utente).
- Una sola connessione attiva: una nuova sostituisce la precedente.
- Frame massimo 256 KB; frame non valido = connessione chiusa (1007); un frame `COMMAND` dall'Edge = errore di protocollo.

## Messaggi (busta `ChannelEnvelope`, contratti in `be/contracts`)
| Verso | Tipo | Scopo |
| --- | --- | --- |
| Edge→Hub | HELLO | apre la sessione; l'Hub rinvia tutti i comandi aperti |
| Edge→Hub | HEARTBEAT | salute e servizi (ogni 30 s); l'Edge risulta collegato se l'ultimo è più recente di 90 s |
| Hub→Edge | COMMAND | `SignedCommand` firmato Ed25519, con id, utente, permessi, scadenza (5 min) e nonce |
| Edge→Hub | ACK | comando ricevuto e salvato |
| Edge→Hub | RESULT | SUCCEEDED / FAILED / REJECTED; un risultato ripetuto è ignorato |
| Edge→Hub | PROJECTION | proiezione in sola lettura; solo nomi in lista bianca (`system.status`, `ai.models`), JSON valido, max 64 KB, versione crescente, chiave di idempotenza |

## Consegna almeno-una-volta
Comando → tabella `command_outbox` (PENDING) → inviato (SENT) → ACKED → esito. Ogni 15 s i comandi SENT non confermati da più di 30 s vengono rinviati; quelli scaduti senza consegna diventano EXPIRED. L'Edge deve usare nonce e id per non rieseguire un comando ripetuto.

## Comandi della Release 1
`system.ping`, `ai.models.list` (SYSTEM_READ), `ai.model.test`, `ai.model.assign` (SETTINGS_MANAGE). Il permesso è controllato dall'Hub **e** di nuovo dall'Edge.

## Accoppiamento
1. Un amministratore con `INTEGRATIONS_MANAGE` chiama `POST /api/integrations/edge/credential`: la credenziale compare **una sola volta**.
2. Copia la credenziale nel portachiavi dell'Edge e la chiave pubblica da `GET /api/integrations/edge/public-key`.
3. La chiave privata di firma dell'Hub sta in un file con permessi 600 (`HUB_SIGNING_KEY_FILE`), creato al primo avvio.

## Esiti dei comandi lunghi
`POST` → `202 {commandId}`; poi `GET /api/commands/{id}` (visibile solo a chi l'ha richiesto).

## Lato Edge (`be/edge`)
- **Rete:** l'Edge ascolta solo su `127.0.0.1:8081` (solo `/actuator/health`; tutto il resto è negato). Non accetta connessioni dalla rete: lavora solo tramite il canale in uscita.
- **Connessione:** `HubConnection` si collega con backoff esponenziale (1 s → 60 s, ±20 % di jitter) e riparte da solo. `wss://` obbligatorio; `ws://` solo con `EDGE_HUB_ALLOW_INSECURE=true` (prove locali). Con `EDGE_HUB_CA_FILE` si considera fidata solo quella CA (pinning). Senza URL, credenziale o chiave pubblica lo stato è NOT_CONFIGURED (nuovo tentativo ogni 30 s, nessun arresto).
- **Segreti:** nel portachiavi macOS, servizio `it.adprogetti.zio.edge`: `hub-edge-key` e `hub-command-public-key`. Si inseriscono con `scripts/edge-set-secret.sh <nome>` (valore digitato a prompt nascosto). Vengono riletti a ogni tentativo di connessione: una rotazione non richiede il riavvio.
- **Comando in arrivo:** inbox (id univoco) → verifica firma Ed25519, scadenza e nonce **nella stessa transazione** (nonce persistenti) → ACK → policy propria dell'Edge (tipo noto e permesso presente) → validazione del payload → esecuzione sequenziale → risultato. Un id già visto non viene rieseguito: si rinvia il risultato salvato. Al Hub arrivano solo codici brevi (`BAD_SIGNATURE`, `EXPIRED`, `REPLAYED`, `POLICY`, `UNKNOWN_TYPE`, `INVALID_PAYLOAD`, `UNSUPPORTED`, `EXECUTION_ERROR`, `INTERRUPTED`), mai testi d'errore.
- **Riavvio durante un comando:** un comando ricevuto ma non finito non viene rieseguito (potrebbe avere avuto effetti): si chiude come FAILED/INTERRUPTED e l'Hub viene avvisato.
- **Outbox:** ACK, risultati e proiezioni sono salvati prima di essere inviati. L'Hub non conferma i risultati, quindi a ogni riconnessione si rinviano anche i messaggi inviati negli ultimi 5 minuti (l'Hub ignora i duplicati). Conservazione 24 ore.
- **Heartbeat:** ogni 30 s con lo stato reale di database e Ollama (`UP/DOWN/NOT_CONFIGURED`), anche subito dopo il HELLO.

## Accoppiamento passo per passo
1. `bash scripts/start-hub.sh`, poi accedere come amministratore.
2. `POST /api/integrations/edge/credential` → copiare la credenziale (compare una volta sola); `GET /api/integrations/edge/public-key`.
3. `bash scripts/edge-set-secret.sh hub-edge-key` e `bash scripts/edge-set-secret.sh hub-command-public-key`.
4. Impostare `EDGE_HUB_URL` in `.env` e `bash scripts/start-edge.sh`.
5. `GET /api/system/status` deve mostrare `edgeConnected: true`.
