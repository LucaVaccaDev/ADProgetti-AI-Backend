# Progetto Zio Pino – Menu e contenuto delle pagine (vincolante) · versione 1.2

Sostituisce la versione 1.1. Da usare insieme a `docs/GRAFICA.md` (versione 1.2) e al capitolato (Allegato F).
Riferimento visivo: `docs/schermate/01-Panoramica.png` … `10-Scheda-gara.png` e `00_Panoramica_di_tutte_le_pagine.png`.
Tutti i dati mostrati nelle schermate (nomi, commesse, importi, CIG) sono segnaposto: in produzione arrivano dal database.

---

## 1. Struttura generale

- **Barra laterale** trasparente, apribile/chiudibile, uguale in tutte le pagine (GRAFICA.md §4).
- **Intestazione** di ogni pagina: titolo e sottotitolo a sinistra; a destra solo data e notifiche.
- Ogni pagina rispetta i permessi del capitolato (C.2): ciò che l’utente non può vedere non compare.

## 2. Barra laterale

1. Logo AD (cerchio blu) + "Zio Pino · AD Progetti srl". Clic → Panoramica.
2. Campo **Cerca…** (ricerca globale: commesse, gare, email, protocolli, documenti).
3. **Menu**: Panoramica · Segreteria (contatore: email da protocollare/verificare) · Gare (contatore: gare compatibili nuove) · BIM · Foto · Ricerca e chat.
4. **Studio**: Calendario (contatore: impegni di oggi) · Commesse · Amministrazione (solo ruoli autorizzati).
5. In fondo: **Chiedi ad Alfonzo** (apre una nuova chat in Ricerca e chat) · **Quota AI online** del mese dell’utente · **utente collegato** (avatar, nome, ruolo, stato).

---

## 3. Panoramica (pagina iniziale) — schermata 01

1. **Intestazione lilla** con saluto "Buongiorno / Buon pomeriggio / Buonasera <Nome>." e riepilogo del giorno in una frase (email da protocollare al prossimo orario, gare in scadenza nella settimana, soccorsi istruttori aperti); bottone "Vedi cosa fare oggi".
2. Tre schede in riga:
   - **Utenti online**: avatar con iniziali, pallino verde/grigio, nome, ruolo/sezione.
   - **Protocolli della settimana**: numero + confronto con la settimana precedente + barre per giorno.
   - **Gare compatibili**: numero + confronto + riepilogo "in preparazione / presentate / soccorso istruttorio".
3. **Nuove gare per lo studio**: le 3 più rilevanti come schede gara con **indicatore di fattibilità** (GRAFICA.md §5.8); link "Tutte le gare".
4. Colonna destra:
   - **Settimana** (mese corrente, frecce) con oggi evidenziato e pallini sui giorni con impegni; sotto **In arrivo · 7 giorni** (offerte, SAL, soccorsi istruttori, sopralluoghi…).
   - **Prossimo protocollo** (orario 14:30 o 18:30): "Anteprima pronta alle 14:15 · conferma in un clic"; quante in arrivo/inviate, PEC, da verificare; quante in quarantena.
5. Solo amministratore: in fondo una riga **Sistema** (NAS, AI locale, caselle email, registro Excel, uso AI online).

Il contenuto si adatta al ruolo (DL: prima le proprie commesse e scadenze; responsabile gare: prima le gare).

---

## 4. Segreteria — schermata 02

**Schede**: **Anteprima 14:30** (o 18:30, la prossima) · Protocollate · Da verificare · Quarantena · Registro. Sotto-sezioni secondarie: Non associate · Escluse · Annullati · Commesse e fasi da attivare · Interlocutori per fase · Newsletter e disiscrizioni · Mittenti protetti · Recupero pregresso.

- **Anteprima del protocollo** (Allegato F.3): tabella con tipo (PEC / Email / Inviata), mittente e oggetto, **commessa proposta**, **numero** che verrà assegnato (`N_AAAA_MM_GG_CR/CI`), stato (Pronta · Collegata alla gara · Da verificare · Quarantena). Bottone **"Conferma protocollo"**; nota "Si annulla entro un’ora".
- **Da verificare**: per ogni email proposta (commessa › fase), motivazione, confidenza, percorso NAS; "Conferma e protocolla", "Cambia commessa o fase", "Non protocollare".
- **Quarantena**: mittente, oggetto, casella, motivo, giorni rimanenti (30); "Recupera", "Elimina subito".
- Colonna destra:
  - **Caselle email**: una riga per casella (connessa, ultimo controllo; PEC "sola lettura"), etichetta "N di 6 connesse".
  - **Scadenza trovata da Alfonzo** (Allegato F.4): frase trovata nella comunicazione, data proposta, commessa; bottone "Aggiungi al calendario".

---

## 5. Gare

### 5.1 Prima pagina — schermata 03
- **Filtri**: Tutte · Compatibili · Da verificare · Appalti integrati · Scartate; bottoni "Filtri" e **"Inserisci gara"**.
- **Griglia di schede gara** (GRAFICA.md §5.9) con: etichetta di tipo (OEPV, Prezzo più basso, Appalto integrato, Tempo stretto · N gg, Analisi incompleta), etichetta di stato, **indicatore di fattibilità** colorato secondo Allegato F.2, titolo, ente, importo servizi, scadenza, ID opere, icone "apri" e "salva", **Probabilità storica** (Allegato E.1).
- Colonna destra: **Stato gare** (Individuate oggi · In analisi · In preparazione · Presentate · Soccorso istruttorio, con numero e barra) e riquadro blu **"Alfonzo · ricerca di stanotte"** (pubblicazioni analizzate, quante superano il prefiltro, quante compatibili, gare con documenti da caricare a mano).
- Il sistema **non propone partner o RTP**: indica solo cosa manca e di quanto (Allegato E.1).
- Le gare individuate sono visibili solo al responsabile gare; quelle assegnate anche agli assegnatari.

### 5.2 Scheda gara — schermata 10
- **Intestazione lilla**: etichette tipo, CIG, scadenza; titolo; ente, importo servizi, ripartizione punteggi.
- **Schede**: Riepilogo · Criticità · Requisiti · Punteggio · Offerta tecnica · Amministrativa · Invio.
- **Criticità** (Allegato E.2): livello ALTA/MEDIA/BASSA, titolo, spiegazione, fonte (documento, pagina, articolo), azione ("Bozza quesito" / "Spunto offerta"); riquadro giallo per i documenti **non scaricabili** con bottone "Carica".
- Colonna destra: **Punteggio stimato** (anello con totale, tecnica con suggerimento, economica con ribasso ed equilibrio, zona di anomalia) e **Requisiti** (soddisfatto / mancante con importo / da verificare con scadenza).

### 5.3 Spazio di lavoro della gara (dopo "Partecipa e prepara")
- Percorso "Gare › In preparazione › G_XX", ente, CIG, criterio, scadenza, selettore **Carta intestata**.
- **Avanzamento in 8 fasi**: Analisi · Requisiti · Criteri · Amministrativa · Offerta tecnica · Economica · Verifica e invio · Archivio NAS.
- **Lavori da fare** (compito, stato, responsabile, azione) · **Alfonzo ha già preparato** (azioni automatiche svolte) · **Gruppo di lavoro** · **Pacchetto pronto da caricare** e **verifica firme** (Allegato E.5).

### 5.4 Altre sezioni Gare
Gare in preparazione · Presentate ed esiti (Allegato E.6) · Archivio storico · Referenze e certificazioni · Professionisti e abilitazioni · Documenti dello studio con scadenze (Allegato E.3) · Rete partner (solo archivio consultabile) · Carte intestate.

---

## 6. BIM — schermata 04

- **Intestazione lilla**: stato della postazione (PC, Revit 2027 aperto, rvt-mcp attivo), "Modellazione impianti con Claude", commessa e modello di lavoro; bottoni **"Modellazione BIM"** (apre Claude sul progetto condiviso del Team) e "Copia contesto commessa".
- **Tre numeri con confronto**: elementi creati nella settimana · prove senza anomalie · connettori aperti.
- **Registro operazioni** (NAS › BIM_AI › registro): data, modalità (Prova / Definitivo), etichetta AI_* e descrizione, utente.
- Colonna destra: **Contesto commessa** (modello di lavoro, riferimenti in sola lettura, scheda regole, esclusioni) e riquadro blu **"Regola dello studio"** (prima PROVA, poi DEFINITIVO annullabile in un passo).

## 7. Foto — schermata 05

- **Confronto**: originale (mai modificata) e anteprima ritoccata affiancate, nome del nuovo file; "Salva in FOTO_AI", "Rifai", "Scarta".
- Colonna destra: **Operazioni** (luce ed esposizione, raddrizza verticali, cielo più pulito, colore, rimuovi oggetti) + istruzioni libere + "Elabora"; **Quota immagini del mese** con confronto e avviso "La foto viene inviata a servizi AI online gratuiti, senza posizione GPS né metadati".
- **Ultime elaborazioni**: data, descrizione, cartella di destinazione.

## 8. Ricerca e chat (Alfonzo) — schermata 06

- Colonna conversazioni: "Nuova chat", elenco personale per data (Oggi, Settimana scorsa…), rinominabile ed eliminabile dal proprietario.
- Conversazione: titolo; selettore **"Archivio studio · AI locale" / "Ricerca generale · online"**; domanda (bolla blu) e risposta con riferimenti numerati e **Fonti** (protocollo o percorso NAS), cliccabili.
- Campo di scrittura a pillola con allegato e invio; nota "Le chat sono personali: nessun altro utente le vede, amministratore compreso".

## 9. Calendario — schermata 07

Viste Giorno / Settimana / **Mese**; filtro "Solo i miei / Tutto lo studio"; legenda per tipo (DL, Gare, Appuntamenti, Amministrazione); bottone "Nuovo impegno"; conteggio impegni del mese con confronto. Ogni evento è collegato a commessa e protocollo di origine, con storico delle modifiche.

## 10. Commesse — schermata 08

Filtri Tutte · Attivate · Da attivare · Chiuse; bottone "Rileva dal NAS". Tabella: codice, commessa, fase, responsabile, avanzamento, prossimo passo, stato (Attivata · Da attivare · Post-chiusura). Colonna destra: commesse attive e scadenze a 30 giorni (con confronto), elenco **Da attivare** con bottone "Attiva". La scheda della commessa resta quella del capitolato (fasi, interlocutori, protocolli, scadenze e sospensioni DL, documenti, foto, registro BIM, attività post-chiusura).

## 11. Amministrazione — schermata 09

Schede: Panoramica · Utenti e ruoli · Modelli AI · Quote AI online · NAS e percorsi · Carte intestate · Backup · Registro d’uso.
Panoramica: tre numeri con confronto (utenti attivi, richieste AI della settimana, uso AI online); **Utenti e ruoli** (nome, ruolo, ultimo accesso, "Aggiungi utente"); **Stato del sistema** (NAS, caselle email, AI locale con memoria in uso, backup); **Quote AI online** per persona.
Contenuto delle altre schede come capitolato (utenti online, caselle email, modelli AI, FreeLLMAPI e quote dinamiche, NAS e file certificazioni, carte intestate e modelli, backup e prove di ripristino, registro d’uso e audit senza contenuto delle chat).

---

## 12. Testi fissi dell’interfaccia

- Nome dell’app: **Progetto Zio Pino** (nome provvisorio, nel titolo della finestra e nella PWA); nella barra laterale "Zio Pino · AD Progetti srl".
- Nome dell’assistente: **Alfonzo**, sempre con la z; la z è specchiata nel logotipo e nei pulsanti. Testi: "Chiedi ad Alfonzo", "Alfonzo ha già preparato", "Scadenza trovata da Alfonzo", "Alfonzo · ricerca di stanotte".
- Lingua: italiano; date "Giovedì 5 novembre 2026"; importi "€ 184.300"; numeri sempre con confronto ("+6 rispetto alla settimana scorsa").
