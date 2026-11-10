# Accesso al NAS (Edge)

Specifica: capitolato sez. 5 (5.1 compatibilità macOS, 5.2 modalità di accesso). Codice: `be/edge/.../nas`.

## Come funziona
- Il NAS (192.168.1.57) si monta **con SMB da macOS**; le credenziali stanno nel portachiavi di macOS, mai nel codice né nella configurazione. L'Edge vede solo i punti di montaggio.
- Ogni accesso passa da un solo punto (`NasPathResolver`): il percorso logico (`archivio:/Cartella/File.pdf`) viene validato, risolto con `toRealPath` e controllato contro le radici consentite per la modalità richiesta. Fuori da quel punto il codice non apre percorsi NAS.
- Tre modalità: `INDEX_READ` (solo lettura), `PROTOCOL_WRITE`, `TENDER_WRITE`. Una condivisione che non compare in nessuna modalità (la **Temporanea**) non è raggiungibile.
- Scritture: file temporaneo nella stessa cartella, verifica SHA-256, rinomina; **mai sovrascrittura**; **nessuna cancellazione** (un annullamento è uno spostamento, deciso dal modulo protocollo). Ogni scrittura e ogni rifiuto vanno nell'audit locale dell'Edge, senza contenuti.
- Gare: una cartella nuova si crea solo dopo approvazione e riceve un file nascosto `.zio-tender`; i documenti approvati si archiviano solo in cartelle con quel segno. Le gare già presenti (storiche) non si toccano.
- Un punto di montaggio che in realtà è una cartella locale vuota (NAS non montato) conta come **non disponibile**, per non scrivere per errore sul disco del Mac.

## Configurazione (`edge.nas.*`, variabili `EDGE_NAS_MOUNT_*`)
| Condivisione | UNC | Mount predefinito |
| --- | --- | --- |
| archivio | `\\192.168.1.57\Archivio` | `/Volumes/Archivio` |
| gare | `\\192.168.1.57\Gare` | `/Volumes/Gare` |
| documenti | `\\192.168.1.57\Documenti` | `/Volumes/Documenti` |
| temporanea | `\\192.168.1.57\Temporanea` | `/Volumes/Temporanea` (esclusa) |

Radici per modalità (predefinite): lettura = archivio, documenti, gare; scrittura protocolli = `archivio:/Protocolli` (**da confermare** con la struttura reale); scrittura gare = `gare:/`. Soglia di spazio libero: 5 GB.

## Montare il NAS sul Mac Mini (da fare una volta)
1. Finder › Vai › Connetti al server › `smb://192.168.1.57/Archivio` (poi Gare, Documenti). Salva la password nel portachiavi.
2. Verifica che i volumi compaiano in `/Volumes/Archivio`, `/Volumes/Gare`, `/Volumes/Documenti`.
3. Per il riavvio automatico: Impostazioni › Generali › Elementi login › aggiungi i tre volumi (o usa un utente dedicato in sola lettura per la prima prova).
4. Controlla la riga «NAS» in Panoramica: da «non raggiungibile» deve passare a «collegato».

## Prova in sola lettura sull'SSD (10/11/2026)
Con `bash scripts/start-edge.sh --spring.profiles.active=ssdtest` l'Edge vede soltanto `GARE 2024` e `GARE 2025` dell'SSD `/Volumes/AI` (come condivisione `gare`, solo `INDEX_READ`); nessuna scrittura, nessun'altra condivisione, quindi anche il NAS vero, se montato, non è raggiungibile. In Panoramica il NAS risulta «1 su 4». Test: `NasSsdReadOnlyTrialTest`. Per tornare alla configurazione normale riavviare senza il profilo.

## Limiti noti
- Provato solo su una cartella locale di prova (link simbolici, nomi anomali, scritture concorrenti, hash): **non** ancora sul NAS vero.
- Una condivisione SMB che si blocca può fermare una scrittura già avviata (le verifiche preliminari hanno invece un tempo massimo).
- Il controllo anti-traversal è fatto prima dell'uso e ripetuto prima di creare cartelle; un collegamento simbolico scambiato nell'istante tra controllo e uso richiederebbe accesso in scrittura alla condivisione.
