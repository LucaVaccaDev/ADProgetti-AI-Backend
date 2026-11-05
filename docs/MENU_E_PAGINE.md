# Progetto Zio Pino – Menu e contenuto delle pagine (vincolante)

Da usare insieme a `docs/GRAFICA.md` (stile, colori, componenti) e al capitolato.
Riferimento visivo: `docs/materiali/riferimento_grafica_panoramica.png`.
Tutti i dati mostrati negli esempi (nomi, commesse, importi) sono segnaposto: in produzione arrivano dal database.

---

## 1. Struttura generale

- **Menu laterale** fisso a sinistra (vetro satinato, vedi GRAFICA.md §5.1), uguale in tutte le pagine.
- **Intestazione** in alto a ogni pagina: percorso/categoria (12 px muted), titolo, azioni della pagina a destra.
- Nella sola Panoramica l’intestazione mostra saluto, ricerca globale, notifiche e utente loggato.
- Ogni pagina rispetta i permessi del capitolato (C.2): ciò che l’utente non può vedere non compare.

---

## 2. Menu laterale

Dall’alto verso il basso:

1. **Logo**: cerchio blu AD (#004890) con le sole lettere "AD" bianche, centrato. Clic → Panoramica.
2. **AI Alfonzo**: pillola piccola sotto il logo: "AI" in blu + "Alfonzo" con **la sola lettera z specchiata**. Clic → apre la chat con Alfonzo (Ricerca e chat).
3. **Assistenti** (etichetta di sezione):
   - Panoramica
   - Segreteria
   - Gare
   - BIM
   - Foto
   - Ricerca e chat
4. **Studio** (etichetta di sezione):
   - Calendario
   - Commesse
   - Amministrazione (visibile solo ad amministratore e ruoli autorizzati)
5. **Utenti online · N di M**: elenco degli utenti collegati (avatar con iniziali, pallino blu online / grigio inattivo, nome, sezione dell’app in cui si trova). Massimo 6 visibili, poi "e altri N".
6. In fondo: **Quota AI online del mese** dell’utente (barra + "38% usato · 21% a fine ottobre").

Voce attiva: pillola blu piena con testo bianco. Icone a linea per le voci "Assistenti"; solo testo per "Studio".

---

## 3. Panoramica (pagina iniziale)

1. **Intestazione**: data del giorno; titolo "Buongiorno / Buon pomeriggio / Buonasera, <Nome Cognome dell’utente loggato>"; a destra ricerca globale (pillola), pulsante notifiche, utente loggato (avatar blu con iniziali + nome + ruolo).
2. **Barra "Da ieri"** (riquadro blu pieno): novità dall’ultimo accesso (nuovi protocolli, FAQ di gara, scadenze spostate, script BIM confermati…) + scorciatoie: "Nuovo appuntamento", "Carica gara", "Apri modellazione BIM" (quest’ultimo pulsante bianco).
3. **Calendario mensile** (riquadro largo, al centro): giorni con impegni cerchiati (azzurro con numero blu) e "N impegni" sotto; oggi in cerchio blu pieno; giorno selezionato evidenziato; frecce mese precedente/successivo; filtro "Solo i miei / Tutto lo studio"; conteggio impegni del mese con confronto mese precedente.
4. **Impegni del mese** (accanto al calendario): elenco con data, icona del tipo (DL, appuntamento, gara, amministrazione), titolo e dettaglio; clic su un giorno del calendario → mostra solo quel giorno; link "Apri calendario".
5. **Tre numeri del giorno**: Email da verificare (primo riquadro, blu pieno), Gare nuove da valutare, Le tue attività aperte — ognuno con confronto.
6. **Tre elenchi**: Le tue commesse (fase, barra di avanzamento, prossimo passo) · Gare in corso (scadenza e stato) · Ultimi protocolli (numero e descrizione).
7. **Riga Sistema** (solo amministratore): NAS, AI locale, caselle email IMAP (N di 6 connesse), registro Excel, consumo AI online dello studio.

Il contenuto si adatta al ruolo: chi segue la DL vede prima le proprie commesse e scadenze; il responsabile gare vede le gare da valutare.

---

## 4. Segreteria

**Intestazione**: "Segreteria › Protocollo"; azioni "Registro Excel", "Protocolla ora".

**Schede** (sottolineate, con conteggio):
- **Da verificare** – email che l’AI non ha protocollato da sola: per ognuna tipo (ricevuta/inviata), casella, data, confidenza e soglia, oggetto, mittente/destinatari, **proposta** (commessa › fase), motivazione, percorso della cartella sul NAS; pulsanti "Conferma e protocolla", "Cambia commessa o fase", "Non protocollare".
- **Protocollate oggi** – elenco con numero (formato `N_AAAA_MM_GG_CR/CI`), commessa, fase, oggetto.
- **Non associate** – email esterne non legate a commesse attivate, con eventuale proposta.
- **Escluse** – email non protocollate con il motivo (es. interne tra colleghi).
- **Quarantena** – email spostate per spam: mittente, oggetto, casella, motivo, giorni rimanenti all’eliminazione (30); pulsanti "Recupera" e "Elimina subito"; filtro per casella.
- **Annullati** – protocolli annullati con utente, data e motivo.

**Colonna a destra**: Elaborazioni di oggi (14:30 e 18:30 con esiti, media giornaliera con confronto) · Ultimi protocolli.

**Altre sezioni della Segreteria** (sotto-menu o schede secondarie): Commesse e fasi da attivare · Interlocutori per fase · Newsletter e disiscrizioni proposte · Mittenti protetti · Recupero pregresso (una commessa alla volta).

---

## 5. Gare

### 5.1 Prima pagina: "Gare a cui puoi partecipare"
- **Intestazione**: titolo, "N nuove compatibili questa settimana · M la settimana scorsa · aggiornato alle HH:MM"; azioni "Gare in preparazione N", "Carica gara".
- **Filtri** (schede): Tutte le compatibili · Da solo · Con RTP · Da verificare · Appalti integrati su segnalazione · Scartate.
- **Scheda gara** (una per gara):
  1. Riga dati: stato (nuova, data pubblicazione), ente, CIG; titolo; procedura, criterio, ID opere; a destra **Importo servizi**, **Scadenza** (con "tra N giorni"), **Compatibilità %**.
  2. **Requisiti**: un’etichetta per requisito con importo disponibile e richiesto (es. "E.08 · 12,46 M€ su [richiesto]") e pallino: grigio = soddisfatto da solo, blu = serve RTP, scuro = da verificare; sotto una riga di spiegazione (equivalenze, mancanze).
  3. **Come partecipare** (riquadro a destra): **Da solo** / **In RTP** (con partner suggerito dalla rete partner) / **Progettista indicato** o **associato** (appalti integrati) / **Da verificare** (cosa chiarire), con punteggio stimato.
  4. Pulsanti: "Partecipa e prepara", "Vedi analisi completa", "Scarta".
- Le gare individuate sono visibili solo al responsabile gare; le gare assegnate anche agli assegnatari.

### 5.2 Dopo "Partecipa e prepara": spazio di lavoro della gara (G_XX)
- **Intestazione**: percorso "Gare › In preparazione › G_XX", titolo, ente, CIG, criterio, modalità (da solo/RTP), scadenza; selettore **Carta intestata**; pulsante "Chiedi ad Alfonzo".
- **Avanzamento in 8 fasi**: Analisi · Requisiti · Criteri · Amministrativa · Offerta tecnica · Economica · Verifica · Archivio NAS.
- **Lavori da fare**: elenco compiti con stato, dettaglio, responsabile e azione (Rivedi, Apri matrice, Verifica i campi, Apri bozza, Apri editor, Simula).
- **Alfonzo ha già preparato**: elenco delle azioni automatiche (documenti scaricati, spazio creato, scadenze in calendario, offerte simili trovate, DGUE compilato, controllo FAQ attivato).
- **Gruppo di lavoro** (persone e ruoli, "Aggiungi persona") e **Punteggio stimato** (con confronto con gara simile).

### 5.3 Scheda di analisi completa della gara
Schede: Requisiti (tabella requisito / richiesto / disponibile / esito) · Criteri e punteggi · Busta amministrativa · Offerta tecnica (editor nella Web App) · Offerta economica (simulazione ribassi) · FAQ e rettifiche · Concorrenti (dati pubblici ANAC).

### 5.4 Altre sezioni Gare
Gare in preparazione · Archivio storico (gare partecipate, esiti, punteggi) · Referenze e certificazioni (dal file Excel dello studio, con differenze all’ultimo import) · Rete partner · Carte intestate.

---

## 6. BIM

- **Intestazione**: "BIM · Modellazione impianti"; selettore Commessa.
- **Riquadro principale**: stato della postazione (PC, Revit 2027 aperto, rvt-mcp attivo); testo "Apri Claude sul progetto AD BIM – Modellazione"; pulsanti **"Modellazione BIM"** (apre Claude Desktop/claude.ai sul progetto condiviso del Team) e **"Copia contesto commessa"**.
- **Contesto commessa**: modello di lavoro, modelli di riferimento in sola lettura, scheda regole (pendenze, giochi, quote di posa), esclusioni.
- **Tre numeri**: elementi creati nella settimana · prove senza anomalie · connettori aperti (con confronto).
- **Registro operazioni** (da `BIM_AI\registro` sul NAS): data, modalità (Prova/Definitivo), etichetta e descrizione, utente.

---

## 7. Foto

- **Intestazione**: "Foto · Ritocco rapido"; azioni "Fotoinserimento", "Carica foto".
- **Confronto**: foto originale (mai modificata) e anteprima ritoccata affiancate, con nome del nuovo file; pulsanti "Salva in FOTO_AI", "Rifai", "Scarta".
- **Operazioni**: caselle (luce ed esposizione, colore, raddrizza verticali, rimuovi oggetti, cielo più pulito) + istruzioni libere + "Elabora".
- **Quota immagini del mese** con confronto e avviso: "La foto viene inviata a servizi AI online gratuiti, senza posizione GPS né metadati".
- **Ultime elaborazioni**: data, descrizione, cartella di destinazione.

---

## 8. Ricerca e chat (Alfonzo)

- **Intestazione**: titolo della conversazione; selettore **"Archivio studio · AI locale" / "Ricerca generale · online"**.
- **Colonna conversazioni**: "Nuova chat", elenco personale raggruppato per data (Oggi, Settimana scorsa…), rinominabile ed eliminabile dal proprietario.
- **Conversazione**: domanda dell’utente; risposta di Alfonzo con riferimenti numerati [1], [2]…; **Fonti** in fondo (titolo documento e numero di protocollo o percorso), cliccabili.
- **Campo di scrittura** a pillola con pulsante di invio; possibilità di allegare file e immagini.
- Nessuna chat è visibile ad altri utenti, amministratore compreso.

---

## 9. Calendario

Vista mese / settimana / giorno; filtri "Solo i miei / Tutto lo studio" e per tipo (DL, appuntamenti, gare, amministrazione); creazione e modifica manuale; ogni evento collegato a commessa e protocollo di origine; storico delle modifiche (spostamenti, annullamenti).

---

## 10. Commesse

Elenco commesse rilevate dal NAS (attivate / non attivate) con fase, responsabile, avanzamento; scheda commessa con fasi (PFTE, Esecutivo, DL), interlocutori per fase, protocolli, scadenze e sospensioni DL, documenti, foto, registro BIM, attività e attività post-chiusura; azione "Attiva protocollo" per fase.

---

## 11. Amministrazione

- **Utenti e ruoli** (amministratore, collaboratore, responsabile DL, responsabile gare, amministratore BIM, abilitati protocollo/referenze).
- **Utenti online** (dettaglio con ultimo accesso).
- **Caselle email** (una per casella: connessa, ultimo controllo, messaggi acquisiti, errori).
- **Modelli AI**: modelli installati, memoria, assegnazione modello → funzione, test rapido, ripristino.
- **FreeLLMAPI e quote**: gruppi TESTO/IMMAGINI, riserva mensile, ripartizione dinamica per utente con proposta di ricalcolo mensile.
- **NAS e percorsi**, cartelle escluse (vuoto), file certificazioni.
- **Carte intestate**, modelli Word, registro Excel.
- **Backup** (ultimo backup NAS ed esterno, prove di ripristino).
- **Registro d’uso AI e audit** (senza contenuto delle chat).

---

## 12. Testi fissi dell’interfaccia

- Nome dell’app: **Progetto Zio Pino** (nome provvisorio, nel titolo della finestra e nella PWA).
- Nome dell’assistente: **Alfonzo**, sempre con la z; nel logotipo la z è specchiata. Pulsanti e messaggi: "Chiedi ad Alfonzo", "Alfonzo ha già preparato".
- Lingua: italiano; date "Lunedì 2 novembre 2026"; importi "186.400 €"; numeri con confronto ("8 a fine ottobre", "+12% su ottobre").
