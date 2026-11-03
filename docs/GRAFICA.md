# Progetto Zio Pino – Specifica grafica (vincolante)

Stile scelto dal committente: **"Bento" con menu laterale in vetro satinato**.
Riferimento visivo: `docs/materiali/riferimento_grafica_panoramica.png` (Panoramica, desktop 1440 px).
Questa specifica sostituisce le precedenti regole grafiche (capitolato C.16 resta valido dove non in contrasto).

---

## 1. Principi

1. Un solo colore d’accento: **il blu del logo AD Progetti**. Nessun altro colore saturo.
2. Niente nero pieno: dove serve un elemento “forte” si usa il blu AD.
3. Niente gradienti sui componenti. Le uniche sfumature ammesse sono le macchie sfocate dello sfondo, che servono all’effetto vetro del menu.
4. Spaziature solo multipli di 8 px.
5. Due soli pesi di carattere (400 e 600). Niente testi tutto maiuscolo.
6. Niente emoticon: solo icone a linea sottile (stroke 1.5 px), stesso set ovunque.
7. Numeri senza decorazioni (niente badge colorati, frecce, colori sul valore), sempre con il confronto rispetto al periodo precedente.
8. Grafici con linee sottili (1–1,5 px), griglia leggera, colori dalla palette.

---

## 2. Design token (CSS custom properties)

```css
:root {
  /* Colori */
  --ad-blue: #004890;            /* accento unico: logo, azioni, stato attivo */
  --ad-blue-hover: #00366C;
  --ad-blue-soft: #E3ECF7;       /* sfondo giorni con impegni, chip informativi */
  --ad-blue-shadow: rgba(0, 72, 144, 0.25);

  --bg: #EEF0F3;                 /* sfondo pagina */
  --bg-blob-1: #BFD0EA;          /* macchie sfocate dietro il menu (solo sfondo) */
  --bg-blob-2: #D7DCE6;
  --surface: rgba(255, 255, 255, 0.86); /* riquadri */
  --surface-solid: #FFFFFF;
  --surface-muted: #F0F0ED;      /* chip, icone impegni, segmented control */

  --text: #161616;               /* testo principale */
  --text-muted: #6E6E6E;         /* testo secondario, etichette, confronti */
  --line: #F0F0ED;               /* separatori nelle liste */
  --track: #E2E2DF;              /* binario barre di avanzamento */
  --neutral-dot: #B5B5B0;        /* stato neutro / inattivo */
  --on-accent: #FFFFFF;

  /* Vetro satinato (menu laterale) */
  --glass-bg: rgba(255, 255, 255, 0.42);
  --glass-border: rgba(255, 255, 255, 0.70);
  --glass-shadow: 0 12px 40px rgba(20, 40, 80, 0.10), inset 0 1px 0 rgba(255, 255, 255, 0.80);
  --glass-blur: blur(28px) saturate(160%);

  /* Forme */
  --radius-card: 32px;
  --radius-pill: 999px;
  --radius-small: 12px;

  /* Spaziature (multipli di 8) */
  --s1: 8px; --s2: 16px; --s3: 24px; --s4: 32px; --s5: 40px;

  /* Tipografia */
  --font: 'Manrope', system-ui, sans-serif;  /* pesi 400 e 600 */
  --font-mono: 'IBM Plex Mono', monospace;    /* solo numeri di protocollo / codici */
}
```

Font: Manrope (400, 600) e IBM Plex Mono (400) ospitati localmente nella Web App (es. pacchetti `@fontsource/manrope`, `@fontsource/ibm-plex-mono`), nessun caricamento da CDN esterni.

---

## 3. Tipografia

| Uso | Dimensione | Peso | Note |
|---|---|---|---|
| Titolo pagina (es. "Buonasera, Giuseppe Mattera") | 34 px | 600 | letter-spacing −0.02em |
| Titolo riquadro | 17 px | 600 | |
| Testo | 14 px | 400 | |
| Etichette, confronti, date | 12 px | 400 | colore `--text-muted` |
| Numero grande (KPI) | 56 px | 600 | letter-spacing −0.03em, cifre tabellari |
| Codici protocollo | 13 px | 400 | `--font-mono` |

Cifre sempre tabellari (`font-variant-numeric: tabular-nums`).

---

## 4. Layout

- Desktop: menu laterale fisso a sinistra (larghezza 248 px) + contenuto a riquadri.
- Padding pagina 16 px; distanza tra riquadri 16 px.
- Contenuto su griglia: righe a 2 colonne (3fr / 2fr) e a 3 colonne uguali.
- Responsive: sotto 1024 px i riquadri vanno in colonna singola; sotto 768 px il menu laterale diventa una barra superiore con menu a comparsa. Le tabelle larghe scorrono in orizzontale dentro il proprio riquadro.
- Web App installabile come PWA (capitolato C.15).

---

## 5. Componenti

### 5.1 Menu laterale (vetro satinato)
- Sfondo `--glass-bg`, bordo 1 px `--glass-border`, ombra `--glass-shadow`, `backdrop-filter: var(--glass-blur)`, raggio `--radius-card`.
- Dall’alto:
  1. **Logo**: cerchio pieno `--ad-blue` da 104 px con le sole lettere "AD" bianche (immagine estratta dal logo ufficiale), centrato.
  2. **AI Alfonzo**: pillola piccola (altezza 32 px, sfondo bianco 70%), testo 12 px: "AI" in `--ad-blue` peso 600 + "Alfonzo" in `--text`, con **solo la lettera z specchiata** (`transform: scaleX(-1)` sulla z).
  3. Sezione "Assistenti": Panoramica, Segreteria, Gare, BIM, Foto, Ricerca e chat (icona a linea + testo 14 px).
  4. Sezione "Studio": Calendario, Commesse, Amministrazione.
  5. **Utenti online · N di M**: avatar rotondo 32 px con iniziali, pallino di stato 10 px in basso a destra (`--ad-blue` online, `--neutral-dot` inattivo), nome e sezione in cui si trova.
  6. In fondo: quota AI online del mese (barra 4 px) con confronto mese precedente.
- Voce attiva: pillola piena `--ad-blue`, testo bianco 600. Voci normali: testo `--text`, nessuno sfondo.

### 5.2 Sfondo pagina
- Colore `--bg` con due macchie molto sfocate (`filter: blur(120–140px)`) nei colori `--bg-blob-1` e `--bg-blob-2`, posizionate in alto a sinistra (dietro al menu) e in basso al centro. Servono solo all’effetto vetro: nessun gradiente sui componenti.

### 5.3 Riquadri (card)
- Sfondo `--surface`, raggio `--radius-card`, padding 24 px, nessun bordo, nessuna ombra marcata.
- Intestazione: titolo 17/600 a sinistra, link o azione a destra (link in `--ad-blue`).
- Riquadro evidenziato (es. primo KPI della Panoramica, barra "Da ieri"): sfondo pieno `--ad-blue`, testo bianco, etichette bianche al 75%.

### 5.4 Pulsanti
- Altezza 40 px, padding orizzontale 16 px, raggio `--radius-pill`, testo 14 px, icona a sinistra opzionale (16 px).
- Primario: sfondo `--ad-blue`, testo bianco. Su sfondo blu: pulsante bianco con testo `--ad-blue`.
- Secondario: sfondo `--surface-muted`, testo `--text`, nessun bordo. Su sfondo blu: bianco al 12%, testo bianco.
- Terziario: solo testo.

### 5.5 Selettore segmentato
- Contenitore pillola `--surface-muted`, padding 4 px; opzione attiva pillola `--ad-blue` con testo bianco; opzioni non attive solo testo.

### 5.6 Campo di ricerca / input
- Pillola bianca, altezza 40 px, icona lente a sinistra, nessun bordo visibile.

### 5.7 Intestazione pagina
- A sinistra: data (12 px, muted) e titolo grande.
- A destra: ricerca, pulsante notifiche rotondo bianco, **utente loggato**: avatar rotondo pieno `--ad-blue` con iniziali bianche + nome e ruolo.

### 5.8 Calendario mensile
- Settimana da lunedì, intestazioni giorni 12 px muted.
- Cella alta 72 px; numero del giorno in cerchio 32 px.
- Giorno con impegni: cerchio `--ad-blue-soft`, numero `--ad-blue` peso 600, sotto "N impegni" 11 px muted.
- Oggi: cerchio pieno `--ad-blue`, numero bianco.
- Giorno selezionato: sfondo cella `#F4F4F1`, raggio 20 px.
- Filtro "Solo i miei / Tutto lo studio" con il selettore segmentato.

### 5.9 Liste (impegni, commesse, gare, protocolli)
- Righe separate da 1 px `--line`, padding verticale 10–16 px.
- Impegni: data (numero 16 px + giorno 11 px) · icona del tipo in quadratino 32 px (`--surface-muted`, raggio 12 px) · titolo 14/600 + dettaglio 12 px muted.
- Commesse: nome 600 + fase a destra; barra di avanzamento 4 px (`--track` / `--ad-blue`); nota sotto.
- Protocolli: numero in `--font-mono`, descrizione sotto.

### 5.10 KPI
- Etichetta 14 px muted · numero 56/600 · confronto 12 px muted (es. "8 a fine ottobre", "+12% su ottobre").

### 5.11 Stati
- Pallino 8 px: neutro `--neutral-dot`, attenzione/attivo `--ad-blue`. Lo stato è sempre anche scritto a parole (mai solo colore).
- Esiti requisiti gare: pallino neutro = soddisfatto da solo; pallino blu = serve RTP; pallino `--text` = da verificare; più la parola.

### 5.12 Icone
- Set unico a linea (es. Lucide), 16 px nei menu e pulsanti, stroke 1.5 px, colore ereditato dal testo.

---

## 6. Accessibilità
- Contrasto testo ≥ 4.5:1 (anche le etichette 12 px), 3:1 per testi ≥ 24 px.
- Elementi cliccabili reali (`button`, `a`, `input` + `label`), area minima 40×40 px.
- Pulsanti solo icona con `aria-label`.
- Lo stato non va mai comunicato solo con il colore.

---

## 7. Applicazione
- Tutte le schermate (Panoramica, Segreteria/Protocollo, Gare – home e preparazione, BIM, Foto, Ricerca e chat, Amministrazione) usano questi token e componenti.
- I componenti vanno realizzati una volta sola in una libreria UI condivisa del frontend (`packages/ui` o equivalente) e riusati.
- Nessun valore di colore, raggio o spaziatura scritto a mano nelle pagine: solo token.
- Tema scuro: non nel perimetro attuale (eventuale evoluzione futura sugli stessi token).
