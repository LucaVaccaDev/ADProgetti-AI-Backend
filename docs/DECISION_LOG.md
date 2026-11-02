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
| T-05 | 2026-11-02 | Colima + Docker CLI (Homebrew nativo) come runtime di container per Testcontainers; variabili in `scripts/env.sh`. | Docker Desktop non necessario; gratuito e leggero. |
| D-10 | 2026-11-02 | **TOTP non implementato per ora** (deviazione temporanea da D-05, richiesta dal committente per semplificare il login). Lo schema ha già `users.mfa_required` come predisposizione. **Va reintrodotto prima di esporre l'Hub su Internet.** | Semplificazione richiesta in sviluppo. |
| T-06 | 2026-11-02 | Autenticazione: access token JWT (HS256, 15 min, segreto da `HUB_JWT_SECRET`) in header `Authorization`; refresh token casuale a 256 bit, salvato come hash SHA-256, con rotazione e rilevamento del riuso (riuso ⇒ revoca di tutta la famiglia), in cookie `HttpOnly; SameSite=Strict; Path=/api/auth` (`Secure` salvo `HUB_COOKIE_SECURE=false` in sviluppo). Nessun token in `localStorage`. CSRF disattivato: API stateless con bearer, cookie SameSite=Strict solo su `/api/auth`. | CLAUDE.md §9.10. Libreria JWT: Nimbus, già inclusa in Spring Security (nessuna dipendenza nuova). |
| T-07 | 2026-11-02 | Password con BCrypt (costo 12), minimo 12 caratteri. Blocco account dopo 5 errori per 15 minuti; risposta di login sempre generica. | Argon2 richiederebbe BouncyCastle; BCrypt è sufficiente e senza dipendenze extra. |
| T-08 | 2026-11-02 | Primo amministratore creato da riga di comando (`scripts/create-admin.sh`), password digitata al prompt o in `HUB_BOOTSTRAP_ADMIN_PASSWORD`; rifiuta se esiste già un amministratore. | Decisione del committente (D-08: nessuna registrazione pubblica). |
| T-09 | 2026-11-02 | Dipendenze nuove sull'Hub: `spring-boot-starter-data-jpa`, `-flyway`, `flyway-database-postgresql`, `postgresql`, `spring-boot-starter-security-oauth2-resource-server` (JWT), `springdoc-openapi-starter-webmvc-api` 3.2.0 (OpenAPI, solo JSON), test: Testcontainers, `archunit-junit5` 1.5.1. | Stack da CLAUDE.md §7. |
| T-10 | 2026-11-02 | Mancano ancora in build OWASP Dependency-Check e `npm audit` (CLAUDE.md §9.11). Da aggiungere prima della Release 8. | Rimandato per non rallentare le prime iterazioni. |
