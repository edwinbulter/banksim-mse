# 0009. PostgreSQL met Flyway-migraties en JdbcClient

Datum: 2026-10-20
Status: Geaccepteerd

## Context
Slice 1 heeft voor het eerst opslag nodig: `bank-seed` maakt rekeninghouders en rekeningen aan (ADR 0003), `bank-api` leest ze voor het overzicht, en Keycloak heeft een database nodig (ADR 0007). Het origineel gebruikt PostgreSQL 18.6, Flyway in een aparte module `bank-migrate` (als Helm-hook-Job) en `JdbcClient` in plaats van JPA.

Opties voor de migraties: een aparte Job `bank-migrate` (zoals het origineel) of Flyway bij het starten van `bank-api`. Opties voor de toegang: `JdbcClient` (expliciete SQL) of Spring Data JPA.

## Beslissing
- **PostgreSQL** `postgres:18.6-alpine` (op digest, zoals het origineel) als StatefulSet in `banksim-mse`, met een PVC op storage class `standard`. Eén instantie met de databases `bank` (eigenaar `bank`) en `keycloak` (eigenaar `keycloak`), plus een gebruiker `bank_seed` met alleen SELECT en INSERT in `bank` (ADR 0003). PostgreSQL maakt ze bij de eerste start aan, op een lege PVC. De wachtwoorden staan in Secrets, één per gebruiker van het wachtwoord (`postgres`, `bank-api`, `bank-seed`, `bank-bff` en `keycloak`). `install.sh` maakt ze aan als ze nog niet bestaan. Ze staan niet in Git of in `values.yaml`.
- **Flyway bij het starten van `bank-api`**, voorlopig. Er is nu één replica en één schema-eigenaar; een aparte migratie-Job is extra infrastructuur zonder huidig voordeel. Zodra er meer replica's komen (CFR resilience) of een tweede schrijver met eigen rechten, gaat dit naar een `bank-migrate`-Job, met een nieuwe ADR.
- **`JdbcClient`** met expliciete SQL, geen JPA, zoals het origineel. De queries zijn eenvoudig en het saldo wordt berekend, niet opgeslagen.
- **Testcontainers** voor repositorytests in de commit stage.

## Gevolgen
- Het schema bestaat zodra `bank-api` ready is; `bank-seed` kan daarna schrijven.
- Geen TLS of clientcertificaten naar de database in deze slice; dat komt in de CFR-slice zero trust.
- De commit stage heeft Docker nodig voor Testcontainers (op de GitHub-runner aanwezig).

## Bewaking
- Repositorytests met Testcontainers op dezelfde PostgreSQL-versie.
- helm-unittest: PostgreSQL in `banksim-mse`, image op digest, wachtwoorden alleen uit Secrets.
- `scripts/iac-check.sh`: geen enkele env-variabele met PASSWORD, SECRET of WACHTWOORD in de naam heeft een letterlijke waarde in de chart.
- bats: `install.sh` maakt het Secret aan als het ontbreekt en laat een bestaand Secret ongewijzigd.
- Installatiescenario: opnieuw installeren verandert niets. Dat geldt ook voor de StatefulSet, de PostgreSQL-pod, de ConfigMaps en de Secrets (uid, generatie en inhoud).
