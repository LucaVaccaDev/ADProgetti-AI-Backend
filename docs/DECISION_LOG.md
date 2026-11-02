# Registro delle decisioni

Ogni deviazione dal capitolato e ogni scelta tecnica non banale va registrata qui.
Le deviazioni D-01…D-08 sono nel `CLAUDE.md`.

| ID | Data | Decisione | Motivazione |
| --- | --- | --- | --- |
| D-09 | 2026-11-02 | Il repository usa `fe/` (Angular) e `be/` (Maven: `contracts`, `hub`, `edge`) invece di `apps/` e `libs/`. Resta in vigore il resto della §3 del CLAUDE.md (`agents/`, `infra/`, `docs/`, `tests/`). | Struttura già creata dal committente. |
| T-01 | 2026-11-02 | Java 21 LTS e Spring Boot 4.1.2 invece di Java 25. | Ammesso dal CLAUDE.md §7 (21 LTS se necessario); è il JDK installato e supportato dalla toolchain. |
| T-02 | 2026-11-02 | Node 24 LTS e Maven 3.9 installati in `~/tools`, caricati con `source scripts/env.sh`. | Node 25 (di sistema) è una versione dispari, non LTS. |
| T-03 | 2026-11-02 | PostgreSQL 17 + pgvector installati con Homebrew nativo (`/opt/homebrew`) per lo sviluppo. Database `zio_hub` e `zio_edge` con utenti separati. I test useranno comunque Testcontainers (richiede un runtime di container, es. Colima). | Docker non installato. Il `brew` in `/usr/local` è la versione Intel e non funziona su questo Mac Apple Silicon. |
| T-04 | 2026-11-02 | `docs/materiali/` escluso da git. | Può contenere dati reali dello studio (CLAUDE.md §10, sez. 39). |
