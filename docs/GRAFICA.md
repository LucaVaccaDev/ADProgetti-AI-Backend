# Progetto Zio Pino – Grafica (vincolante) · versione 1.2

Sostituisce integralmente la versione 1.1 (stile "Bento" con menu in vetro satinato), che NON va più implementata.
Riferimento visivo obbligatorio: le schermate in `docs/schermate/` (01-Panoramica … 10-Scheda-gara) e `docs/materiali/riferimento_grafica_panoramica.png`.
Da usare insieme a `docs/MENU_E_PAGINE.md`.

---

## 1. Principi

1. **Chiaro, pulito, morbido**: fondo grigio molto chiaro, schede bianche con angoli molto arrotondati, ombre appena percettibili.
2. **Niente nero pieno nelle superfici**: tutto ciò che prima era nero (bottoni principali, etichette, schede selezionate, giorno di oggi, barre, riquadri in evidenza) è **blu AD #004890**. Il testo resta scuro (#141418) per leggibilità.
3. **Lilla come colore di atmosfera**: solo nel riquadro di benvenuto/intestazione delle pagine principali e in alcuni fondi tenui. Non si usa per bottoni o testi.
4. **Colori di stato solo dove servono**: verde/giallo/rosso per fattibilità gare, livelli di criticità e stati; mai come decorazione.
5. **Spaziature multiple di 8** (8, 16, 24, 32). Eccezioni ammesse solo per bordi da 1–3 px.
6. **Due soli pesi di carattere**: 400 e 600.
7. **Icone a linea**, tutte della stessa libreria (es. Lucide), spessore 1,5 px, 16–20 px.
8. **Ogni numero ha il suo confronto** ("38 · +6 rispetto alla settimana scorsa").
9. **Nessun valore grafico scritto a mano nelle pagine**: solo token e componenti della libreria UI condivisa.

---

## 2. Design token

```css
:root {
  /* Fondi */
  --bg-page:   #E9E9EC;   /* fuori dal contenitore dell'app */
  --bg-app:    #F6F6F8;   /* contenitore principale (raggio 32) */
  --bg-card:   #FFFFFF;
  --bg-soft:   #F2F2F5;   /* campi, icone tonde, righe alternate */
  --line:      #E6E6EC;   /* bordi e separatori 1 px */

  /* Testo */
  --text:      #141418;
  --text-2:    #55555F;
  --muted:     #8A8A94;

  /* Accento unico */
  --blue:        #004890;  /* bottoni, selezioni, etichette, contatori, barre, oggi */
  --blue-soft:   #E3ECF7;  /* fondi tenui blu, bottoni secondari evidenziati */
  --blue-line:   #C9D8EA;  /* bordi di bottoni secondari e icone tonde */
  --blue-on:     #FFFFFF;  /* testo su blu */
  --blue-on-2:   #B9CDE6;  /* testo secondario su blu */

  /* Lilla di atmosfera (solo intestazioni e fondi tenui) */
  --lilac-1: #D9D1FA; --lilac-2: #E6E0FC; --lilac-3: #EFE9FE; --lilac-soft: #EEEAFD;
  --hero-gradient: linear-gradient(120deg, var(--lilac-1) 0%, var(--lilac-2) 55%, var(--lilac-3) 100%);

  /* Fondi tenui delle schede gara (in base allo stato) */
  --tint-green:  linear-gradient(135deg, #D7F0E2, #C1E6D2);
  --tint-yellow: linear-gradient(135deg, #FBEFD2, #F6E1AE);
  --tint-red:    linear-gradient(135deg, #F9DDEE, #F3C8E2);
  --tint-lilac:  linear-gradient(135deg, #DCD3FB, #C9BCF8);
  --tint-blue:   linear-gradient(135deg, #DCE8FA, #C8DAF5);

  /* Colori di stato (indicatori di fattibilità, stile iOS) */
  --ok:    #34C759;
  --warn:  #F2B705;
  --ko:    #FF3B30;

  /* Livelli (etichette di criticità e stati) */
  --lv-high-bg: #FDE1E6; --lv-high-fg: #B4233F;
  --lv-mid-bg:  #FCEFD3; --lv-mid-fg:  #8A5A00;
  --lv-low-bg:  #E2F3EA; --lv-low-fg:  #1E7A4B;
  --lv-info-bg: #E3ECF7; --lv-info-fg: #004890;

  /* Forme */
  --radius-app:  32px;
  --radius-card: 28px;
  --radius-tile: 24px;
  --radius-ctl:  16px;   /* campi, voci di menu */
  --radius-pill: 999px;
  --shadow-card: 0 1px 2px rgba(20, 20, 40, .04);

  /* Carattere */
  --font: 'Inter', system-ui, sans-serif;   /* pesi 400 e 600 */
}
```

Font: **Inter 400 e 600**, ospitato localmente (`@fontsource/inter`), nessun caricamento da internet.

Scala testi: titolo pagina 30/600 (letter-spacing −0,02em) · titolo intestazione lilla 30/600 · titolo scheda 17/600 · testo 14/400 · etichette e didascalie 12/400 colore `--muted` · numeri grandi 40–44/600 (letter-spacing −0,03em) · etichette pillola 11–13.

---

## 3. Struttura della pagina

- Il contenuto sta in un **contenitore** `--bg-app`, raggio `--radius-app`, margine 16 dal bordo della finestra, padding 24.
- A sinistra la **barra laterale** (§4), a destra l’area di lavoro.
- **Intestazione dell’area di lavoro**: titolo pagina (30/600) con sottotitolo (13, `--muted`) sotto; a destra solo **data** (pillola bianca con icona calendario) e **notifiche** (tondo bianco 44 px con pallino blu). Ricerca e Alfonzo stanno nella barra laterale.
- Griglia: colonna principale flessibile + colonna destra 320–360 px; spazi tra schede 16–24.

---

## 4. Barra laterale (trasparente, apribile/chiudibile)

- **Trasparente**: nessun fondo e nessuna ombra; separata dall’area di lavoro da una linea verticale 1 px `--line`.
- Larghezza **256 px aperta**, **76 px chiusa** (solo icone). Pulsante tondo 28 px sul bordo destro, in alto, con freccia ‹ / › (bordo `--blue-line`, icona blu). Lo stato aperto/chiuso si ricorda per utente.
- Dall’alto:
  1. **Logo**: cerchio blu 42 px con le lettere "AD" bianche; accanto "Zio Pino" (17/600) e "AD Progetti srl" (11, `--muted`). Chiusa: solo il cerchio.
  2. **Cerca…**: campo bianco, bordo `--line`, raggio 16, icona lente. Chiusa: solo l’icona in un quadrato bianco.
  3. Etichetta **Menu**: Panoramica · Segreteria · Gare · BIM · Foto · Ricerca e chat.
  4. Etichetta **Studio**: Calendario · Commesse · Amministrazione (solo ruoli autorizzati).
  5. In fondo: riquadro **"Chiedi ad Alfonzo"** (bianco, bordo `--line`, raggio 16: tondo blu con "AI", testo "Chiedi ad Alfonzo" con la **z specchiata**, sotto "Commesse, gare, email", freccia →); **Quota AI online** (testo 11 + barra blu); **utente** (avatar con iniziali su `--blue-soft`, pallino verde online, nome e ruolo). Chiusa: solo tondo "AI" e avatar.
- **Voce di menu**: altezza 40, raggio 12, icona 18 + testo 13,5, colore `--text-2`.
  - **Attiva**: fondo bianco, bordo 1 px `--line`, testo `--text` 600, **barra verticale blu 3 × 20 px** sul bordo sinistro della barra laterale.
  - **Contatori** (es. Segreteria 9, Gare 6, Calendario 2): pillola blu, testo bianco 11/600, allineata a destra. Chiusa: pallino blu 8 px sull’icona.

---

## 5. Componenti

### 5.1 Intestazione lilla ("hero")
Presente in Panoramica (saluto), BIM e Scheda gara. Fondo `--hero-gradient`, raggio 28, padding 28–32; a destra un **elemento decorativo circolare** lilla a raggiera (solo qui). Titolo 30/600, testo `#4A4560`, bottone principale blu.

### 5.2 Schede
Bianche, raggio 28, padding 24, `--shadow-card`. Intestazione: titolo 17/600 a sinistra, a destra un’azione (tondo 30 px con bordo `--blue-line` e icona blu, oppure etichetta/testo `--muted`).

### 5.3 Bottoni
- **Principale**: pillola blu, testo bianco 13/600, altezza 40–42, icona a sinistra.
- **Secondario**: pillola bianca, bordo 1 px `--blue-line`, testo blu.
- **Su fondo blu**: pillola bianca con testo blu.
- **Icona tonda**: 30–44 px, bianca, bordo `--blue-line`, icona blu.

### 5.4 Schede di filtro (pillole)
Testo `--text-2`; **selezionata**: pillola blu piena, testo bianco 600. Possono mostrare un conteggio ("Criticità · 4", "Quarantena · 4").

### 5.5 Etichette
- Tipo/categoria (OEPV, Appalto integrato, PEC, Tempo stretto…): pillola blu, testo bianco 11.
- Stato descrittivo (Fattibile, Serve RTP, Su segnalazione…): pillola bianca, testo `--text`.
- Livelli: ALTA/MEDIA/BASSA e stati (Pronta, Da verificare, Quarantena, Collegata alla gara) con le coppie `--lv-*`.

### 5.6 Numeri con confronto
Etichetta 12 `--muted` · numero 40–44/600 · riga di confronto 12 ("**+6** rispetto alla settimana scorsa", il valore in 600, verde se migliora, rosso se peggiora).

### 5.7 Grafico a barre settimanale
Barre arrotondate (raggio 10) su fondo `--lilac-soft`; giorno di oggi **blu pieno**, giorno precedente lilla medio (#C8BCFA). Etichette giorni 11 `--muted`.

### 5.8 Indicatore di fattibilità (stile iOS 26) — sostituisce le sfere
Usato su ogni scheda gara (Panoramica, Gare, Scheda gara).
- **Disco di vetro**: tondo 104 px, fondo bianco al 42%, bordo 1 px bianco all’85%, `backdrop-filter: blur(14px)`, ombra morbida (0 10px 24px rgba(40,40,80,.12)) e riflesso interno in alto.
- **Anello**: diametro 90 px, spessore 10, estremità arrotondate, parte da ore 12 in senso orario; traccia bianca al 65%; leggero bagliore del colore dell’anello.
- **Centro**: percentuale 20/600 ("92%") e sotto "fattibilità" 9 `--text-2`. Per le gare su segnalazione: spunta ✓ e "segnalata".
- **Colore** (regola in Allegato F.2 del capitolato):
  - verde `--ok` se fattibilità **≥ 75%**;
  - giallo `--warn` se fattibilità **< 75%** (soglie configurabili);
  - rosso `--ko` se **serve un RTP** o c’è **un’altra criticità bloccante**, qualunque sia la percentuale;
  - verde pieno con spunta per le gare **su segnalazione** (appalti integrati).
- La **parte alta della scheda gara** (altezza 176) prende il fondo tenue dello stesso stato (`--tint-green`, `--tint-yellow`, `--tint-red`); in alto a sinistra l’etichetta di tipo (blu), in alto a destra l’etichetta di stato (bianca: "Fattibile", "Da valutare", "Serve RTP · IA.02", "Su segnalazione").

### 5.9 Scheda gara
Parte alta colorata con indicatore (§5.8) · titolo 14/600 · ente e importo (importo 600) · riga scadenza e ID opere con icone tonde "apri" e "salva" · "Probabilità storica: **Alta/Media/Bassa/Dati insuff.**".

### 5.10 Elenchi con icona
Icona in quadrato arrotondato 44 px su fondo tenue (lilla, verde, giallo, rosa, azzurro a seconda del tipo), titolo 14/600, dettaglio 12 `--muted`, separatore 1 px.

### 5.11 Barre di avanzamento
Altezza 5, raggio 5, traccia `#F0F0F4`, riempimento blu (verde per "Presentate", rosa per "Soccorso istruttorio" dove serve distinguere).

### 5.12 Calendario
- Settimana (Panoramica): giorni in colonna; oggi in pillola verticale blu con testo bianco; pallino blu sotto i giorni con impegni.
- Mese (Calendario): celle bianche raggio 16, oggi con bordo blu 2 px; impegni come etichette colorate per tipo (DL verde, Gare blu, Appuntamenti lilla, Amministrazione giallo).

### 5.13 Tabelle
Intestazioni 11 `--muted`, righe separate da 1 px `#F0F0F4`, padding 12/8, seconda riga di dettaglio 11 `--muted`. Codici e numeri di protocollo non vanno a capo.

### 5.14 Chat
Messaggio dell’utente: bolla blu, testo bianco, a destra. Risposta di Alfonzo: scheda bianca con bordo, riferimenti [1] [2] in blu, sezione **Fonti** in fondo. Campo di scrittura: pillola bianca con allegato e tondo blu di invio.

### 5.15 Riquadro in evidenza blu
Fondo blu pieno, testo bianco, etichetta `--blue-on-2`, bottone bianco con testo blu (es. "Alfonzo · ricerca di stanotte", "Regola dello studio").

---

## 6. Alfonzo
Nome sempre "Alfonzo"; nel logotipo e nei pulsanti la **z è specchiata** (`display:inline-block; transform: scaleX(-1)` sulla sola lettera). Tondo "AI" blu.

---

## 7. Accessibilità e resa
- Contrasto testo/fondo almeno AA; testo bianco solo su blu pieno.
- Barra laterale chiusa di default sotto 1280 px di larghezza; su tablet/telefono (PWA) diventa un menu a scomparsa.
- Animazioni brevi (150–250 ms) solo per apertura/chiusura barra e comparsa elementi; l’anello di fattibilità si riempie una volta al caricamento.
