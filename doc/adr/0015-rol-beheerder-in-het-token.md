# 0015. De rol `beheerder` is een realm-rol in het access token

Datum: 2026-10-22
Status: Geaccepteerd

## Context
Slice 3. Alleen de beheerder mag de simulatiedatum zetten. Het scenario "Een klant kan de simulatiedatum niet zetten" eist dat een klant 403 krijgt. Tot nu toe kende de realm geen rollen. De BFF krijgt alleen de scopes `basic`, `profile` en `acr`, dus het access token bevat geen rollen. `bank-api` leest alleen `scope` als rechten.

Opties:
1. **Een realm-rol `beheerder`.** `bank-bff` krijgt de standaardscope `roles`, zodat `realm_access.roles` in het token staat. `bank-api` zet die rollen om naar `ROLE_beheerder`.
2. **Een client-rol van `bank-api`** (`resource_access.bank-api.roles`). Dat is preciezer per dienst, maar er is maar één API, en het FO kent rollen voor de hele bank.
3. **Een groep `beheerders`** met een mapper. Dat is een extra concept zonder voordeel nu.

## Beslissing
- **Rol.** De realm krijgt de rol `beheerder` (realm-JSON, met keycloak-config-cli toegepast, ADR 0017).
- **Token.** De client `bank-bff` krijgt de standaardscope `roles`.
- **bank-api.** Een `JwtAuthenticationConverter` zet `realm_access.roles` om naar rechten met het voorvoegsel `ROLE_`. `/api/beheer/**` vereist `hasRole("beheerder")`. De rest blijft `authenticated()`.
- **Seed.** `bank-seed beheerder --alias …` maakt een login met deze rol. De service-account van `bank-seed` krijgt er het recht `view-realm` bij, om de rol op te zoeken. Tegen de echte Keycloak gaf dat zonder dit recht 403; `manage-users` is genoeg om de rol daarna toe te kennen.
- **Klanten.** Klanten krijgen geen rol. "Klant" is wie een rekeninghouder is (eigenaarschap in de query, S6.1).

## Gevolgen
- Rollen van slice 6 en 10 volgen hetzelfde patroon.
- Het access token wordt iets groter.
- De service-account van `bank-seed` krijgt meer rechten in de realm. Dat is acceptabel, want hij maakt al logins aan.

## Bewaking
- Het scenario "Een klant kan de simulatiedatum niet zetten", in alle drivers. Een sabotage zonder `hasRole` maakt het rood.
- Slicetests in `bank-api`: `PUT /api/beheer/simulatiedatum` geeft 401 zonder token, 403 zonder rol en 204 met de rol.
- helm-unittest: de realm-JSON bevat de rol `beheerder` en de scope `roles` voor `bank-bff`.
