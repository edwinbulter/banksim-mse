# 0003. Testdata via een seed-CLI als pod in het cluster

Datum: 2026-10-20
Status: Geaccepteerd; het starten van een pod per aanroep is vervangen door [0020](0020-seed-als-sessie-per-worker.md)

## Context
Het eerste scenario van slice 1 ("Klant ziet de betaalrekening met saldo in het overzicht") heeft per run een eigen klant nodig, met een login in Keycloak en een betaalrekening met een opgegeven saldo in de database. Dat volgt uit de isolatie via aliassen (ADR 0005, werkwijze hoofdstuk 5). Het FO heeft geen scherm om rekeninghouders of rekeningen aan te maken; in het origineel doen de realm-import (vaste gebruikers) en `bank-datagen` dat.

Opties:
1. **Seed-CLI in het cluster.** Een kleine Java-CLI met de regels van `bank-domain` maakt rekeninghouder, Keycloak-gebruiker en rekening aan. De DSL start hem via de shell-driver in `banksim-mse` en leest het resultaat (IBAN, gebruikersnaam, wachtwoord). Starten kan als Kubernetes Job (resultaat uit de log halen na afloop) of als losse pod met `kubectl run --rm --attach` (resultaat direct op stdout).
2. **De DSL schrijft zelf.** De test maakt de Keycloak-gebruiker via de admin-API en de rekening rechtstreeks in PostgreSQL. Snel, maar de test kent dan het schema en de domeinregels, en heeft databasetoegang van buitenaf nodig.
3. **Test-only endpoint** in `bank-api`. Afgewezen: testcode in het systeem, en een extra aanvalsvlak.
4. **Vaste gebruikers uit de realm-import**, zoals het origineel. Afgewezen: geen isolatie tussen parallelle scenario's.

## Beslissing
Optie 1. Een Maven-module `bank-seed` (CLI, hergebruikt `bank-domain`) als image `banksim-mse/bank-seed`, gestart door de shell-driver als losse pod met `kubectl run --restart=Never --rm --attach`, met de alias als argument, bijvoorbeeld `klant --alias jan-<run>-<n> --betaalrekening 1842.17`. De pod schrijft het resultaat als één JSON-regel naar stdout; `kubectl run --attach` geeft die direct terug, zonder te wachten op een Job en daarna de log op te halen. De pod krijgt zijn rechten uit het Secret `bank-seed` (via `envFrom`), dat `install.sh` aanmaakt; de test zelf kent geen wachtwoorden. Start de pod niet binnen 20 seconden, dan faalt de seed met de reden (bijvoorbeeld `ErrImagePull`) en ruimt hij de pod op. Een betaalrekening met saldo wordt aangemaakt als openingssaldo, niet als losse boeking. Keycloak-gebruikers krijgen een gegenereerd wachtwoord dat de seed teruggeeft.

## Gevolgen
- Scenario's zetten hun data op langs de domeinregels, zonder testcode in `bank-api`.
- Een pod starten kost een paar seconden per scenario. Dat is acceptabel voor de huidige omvang.
- `bank-seed` heeft rechten nodig op de database en op de admin-API van de realm. Die rechten staan alleen in het Secret `bank-seed`, niet in de app. In de database is dat een eigen gebruiker `bank_seed` met alleen SELECT en INSERT op de tabellen van `bank-api` (default privileges in `postgres-init`). In Keycloak is het de client `bank-seed` met `manage-users` en `view-users`.
- In slice 11 groeit `bank-seed` uit tot `bank-datagen`: dezelfde code, aangestuurd door profielen.

## Bewaking
- Unittests van `bank-seed`. `JdbcRekeninghouders` draait tegen PostgreSQL via Testcontainers, met het schema van `bank-api`. `KeycloakLogins` draait tegen een nagebootste admin-API (`MockRestServiceServer`). De echte Keycloak komt aan bod in de acceptatiescenario's.
- bats: `install.sh` maakt het Secret `bank-seed` aan als het ontbreekt, en laat een bestaand Secret ongewijzigd.
- De shell-driver start de pod alleen in namespace `banksim-mse` en met het image van de geïnstalleerde versie (`imageTag` uit de Helm-waarden); dat bewaken de acceptatiescenario's die de seed gebruiken.
- De Maven-modules: `bank-api` heeft geen afhankelijkheid op `bank-seed`; zo komt seedcode nooit in de app.
