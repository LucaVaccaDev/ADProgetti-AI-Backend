ALLEGATO F — GRAFICA DEFINITIVA, INDICATORI DI FATTIBILITÀ, ANTEPRIMA DEL PROTOCOLLO, SCADENZE DALLE EMAIL E HARDWARE DI PRODUZIONE (versione 1.2, 11 novembre 2026)

Prevale su ogni altra parte del capitolato e degli allegati in caso di contrasto (in particolare su C.16, C.17, B.10–B.15 per la sola parte grafica, sez. 7.10, 12.2).

F.1 Grafica definitiva
* La grafica della Web App è definita da docs/GRAFICA.md versione 1.2 (PARTE 2 del capitolato unico) e da docs/MENU_E_PAGINE.md versione 1.2 (PARTE 3). Lo stile "Bento" con menu in vetro satinato della versione 1.1 è SUPERATO e non va implementato.
* Elementi principali: barra laterale trasparente, apribile/chiudibile, con ricerca, contatori, "Chiedi ad Alfonzo", quota AI online e utente; intestazioni lilla; unico colore di accento blu AD #004890 (nessun nero pieno nelle superfici); font Inter 400/600; spaziature multiple di 8; icone a linea; numeri sempre con confronto.
* Riferimento visivo vincolante: docs/schermate/01-Panoramica.png … 10-Scheda-gara.png e 00_Panoramica_di_tutte_le_pagine.png. Le 18 schermate della versione 1.1 sono sostituite.
* Tutti i valori grafici sono token e componenti di una libreria UI condivisa; nessun valore scritto a mano nelle pagine.

F.2 Indicatore di fattibilità delle gare
* Su ogni scheda gara (Panoramica, Gare, Scheda gara) l’indicatore grafico è un anello circolare in stile iOS 26 con la percentuale di fattibilità al centro (GRAFICA.md §5.8). Sostituisce le sfere decorative.
* Fattibilità = sintesi calcolata dal sistema della verifica requisiti (sez. 18, E.3) e delle criticità (E.2), sempre con il dettaglio consultabile; non è una decisione di partecipazione.
* Colori (regola del committente):
  - VERDE se la fattibilità è ≥ 75%;
  - GIALLO se la fattibilità è < 75%;
  - ROSSO se è necessario un RTP (requisito mancante) o è presente un’altra criticità bloccante (es. criticità ALTA non risolta, documento obbligatorio scaduto, analisi incompleta per documenti mancanti), indipendentemente dalla percentuale;
  - VERDE con spunta e dicitura "segnalata" per le gare su segnalazione (appalti integrati inseriti a mano su richiesta di un’impresa).
* Le soglie (75%, ed eventuale fascia intermedia 50–75%) sono parametri [CONFIGURABILE] dall’amministratore; valore iniziale: unica soglia 75%.
* La parte alta della scheda gara prende il fondo tenue del colore di stato e mostra un’etichetta di stato ("Fattibile", "Da valutare", "Serve RTP · <requisito>", "Su segnalazione").
* Resta valida E.1: il sistema indica cosa manca e di quanto, non propone partner.

F.3 Anteprima del protocollo (integra sez. 7.10)
* 15 minuti prima di ogni elaborazione programmata (14:15 per le 14:30, 18:15 per le 18:30) il sistema prepara l’ANTEPRIMA: elenco delle comunicazioni che verranno protocollate con tipo (PEC/email ricevuta/inviata), mittente e oggetto, commessa e fase proposte, numero che verrà assegnato, stato (Pronta · Collegata alla gara · Da verificare · Quarantena).
* L’anteprima compare nella Segreteria (prima scheda) e nella Panoramica ("Prossimo protocollo"). Gli utenti abilitati possono correggere commessa/fase, escludere una voce o confermare tutto con "Conferma protocollo".
* Se nessuno interviene, all’orario programmato si procede con le regole di automazione già previste (protocollazione automatica solo dei casi certi; i dubbi restano DA VERIFICARE). La conferma manuale anticipata è possibile.
* Annullamento rapido: entro 60 minuti dall’elaborazione un utente abilitato può annullare l’intera elaborazione o singoli protocolli con un clic. L’annullamento segue la sez. 7.9 (il numero non si riusa, la cartella va in PROTOCOLLI ANNULLATI, si registrano utente, data e motivo); il registro Excel viene aggiornato di conseguenza. Dopo 60 minuti resta solo l’annullamento ordinario.
* Tutte le operazioni sono registrate nell’audit.

F.4 Scadenze trovate nelle comunicazioni (integra sez. 11 e 12.2)
* Quando una comunicazione protocollata (in particolare PEC di enti, RUP, stazioni appaltanti) contiene un termine ("entro 30 giorni", "entro il …", "integrazioni entro …", "termine per le osservazioni …"), l’AI lo individua, calcola la data secondo il testo (giorni naturali salvo diversa indicazione, a partire dalla data indicata o, in mancanza, dalla data di ricezione) e mostra il riquadro "Scadenza trovata da Alfonzo" con: frase originale, data proposta, commessa e fase, protocollo di origine.
* L’inserimento in calendario richiede conferma ("Aggiungi al calendario"); la data è modificabile prima della conferma. Nessun inserimento automatico, nessun calcolo presentato come certo quando il testo è ambiguo (in quel caso il riquadro lo dichiara).
* La scadenza inserita resta collegata al protocollo e alla commessa e compare in Panoramica ("In arrivo") e in Calendario; se arriva una proroga certa, si propone l’aggiornamento con storico (come 12.3).
* Elaborazione sull’AI locale (dati dello studio).

F.5 Hardware di produzione (decisione del committente, integra sez. 2 e l’allegato sull’hardware)
* Macchina di TEST: resta il Mac Mini M4 Pro 24 GB (prototipo e benchmark dell’AI locale).
* Macchina di PRODUZIONE scelta: Apple Mac Studio con chip M5 Max (CPU 18 core, GPU 40 core), 64 GB di memoria unificata, SSD 1 TB, rete 10 Gb Ethernet. Acquisto previsto quando il primo rilascio (protocollo) è pronto per l’uso reale.
* Conseguenze per lo sviluppo:
  - profilo modelli "produzione-64GB": classificazione, modello principale per testi e offerte, modello immagini ed embedding caricati contemporaneamente, con margine per Web App, database e indice; la scelta dei modelli resta configurabile e va verificata con il benchmark (A.1/B) prima della messa in produzione;
  - 1 TB interno per sistema, modelli, database e indice; archivio, commesse, gare e storico email restano sul NAS;
  - accesso al NAS via rete 10 Gb ove disponibile;
  - la procedura di migrazione test → produzione (database, configurazioni, indice, segreti) deve essere provata prima dell’acquisto, senza riscrivere moduli (sez. 2.4).
* Le prestazioni minime della C.13 vanno rimisurate sulla macchina di produzione e riportate in docs/INSTALLATION_MAC.md.
