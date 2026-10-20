# 0007. Keycloak als identiteitsprovider, vanaf slice 1

Datum: 2026-10-20
Status: Geaccepteerd

## Context
Zonder login weet de API niet wie "Jan" is. Een vaste identiteit botst met de isolatie via aliassen, en een identiteit via een header is een authenticatie-bypass. Daarom is besloten (slice 1) om meteen echt in te loggen, in plaats van in slice 6. Het origineel gebruikt Keycloak 26.8.0 met een realm-import op `auth.localtest.me`.

Opties: Keycloak (zoals het origineel); een lichte nep-OIDC-provider (bijvoorbeeld `mock-oauth2-server`) als opstap; een andere IdP.

## Beslissing
Keycloak, dezelfde versie als het origineel (`quay.io/keycloak/keycloak:26.8.0`, op digest), in de chart van `banksim-mse` op host `auth-mse.localtest.me`, met een realm `banksim-mse` uit een realm-import. De realm bevat clients en rollen, geen gebruikers: die maakt `bank-seed` per scenario aan (ADR 0003). Keycloak gebruikt de PostgreSQL van `banksim-mse`, in een eigen database.

De volgorde van de slices verandert: de technische basis van inloggen (Keycloak, BFF, resource server) komt in slice 1. De scenario's over inloggen, uitloggen en toegang blijven in slice 6.

## Gevolgen
- Elk scenario volgt vanaf het begin de echte route door de beveiliging.
- Slice 1 wordt groter: PostgreSQL, Keycloak, `bank-bff` en Spring Security komen tegelijk binnen. Het eerste scenario duurt langer voordat het groen is.
- Ongeveer 1 GB extra geheugen op `kind-single-node` en in CI.
- Brute-force-detectie van Keycloak moet rekening houden met veel testgebruikers (per gebruiker, dus geen probleem bij unieke aliassen).

## Bewaking
- helm-unittest: Keycloak op digest, op `auth-mse.localtest.me`, wachtwoorden en client secrets uit Secrets. De realm-import bevat geen gebruikers met wachtwoord. De namespacecheck in `scripts/iac-check.sh`: alles in `banksim-mse`, geen cluster-brede resources.
- Rooktest in `install.sh`: `https://auth-mse.localtest.me/realms/banksim-mse/.well-known/openid-configuration` geeft 200.
- Elk acceptatiescenario logt in via Keycloak; als Keycloak niet werkt, falen ze allemaal.
