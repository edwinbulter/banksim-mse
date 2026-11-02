# 0037. De frontend kent de rol via `GET /api/ik`

Datum: 2026-11-02
Status: Geaccepteerd

## Context
Scenario "Klant kan niet bij beheer" (`e2e/specs/beheer.spec.ts`, S10.1). Via de API is het al groen: `/api/beheer/**` weigert een klant met een 403 (ADR 0022). In de browser is het rood: een klant die `/beheer` opent, ziet het beheerscherm, en krijgt pas een melding als hij zoekt. De frontend weet niet welke rol de gebruiker heeft.

Het volgende scenario vraagt hetzelfde: de beheerder komt na het inloggen op het beheerscherm, een klant op zijn overzicht (FO "Inleiding en navigatie"). Het plan voor slice 10 noemt hiervoor "één Angular-app met rolafhankelijke routes".

De browser ziet het token niet: de BFF houdt het in de sessie (ADR 0008). bank-api krijgt het token bij elk verzoek en kent de rollen al (`RealmRollen`, ADR 0015).

Opties:
1. **`GET /api/ik` in bank-api.** Geeft de gebruikersnaam en of de gebruiker beheerder is. De frontend vraagt het één keer op, en een guard op de route `/beheer` stuurt een klant naar het overzicht. De BFF blijft een doorgeefluik.
2. **Een endpoint in de BFF** dat de rollen uit het token in de sessie leest. Dan krijgt de BFF eigen logica en een eigen contract naast dat van bank-api (ADR 0034).
3. **Raden aan de hand van een 403**: de frontend vraagt `/api/beheer/rekeninghouders` op en stuurt bij een 403 door. Dat koppelt de navigatie aan een toevallig endpoint, en voor de keuze na het inloggen werkt het niet netjes.

## Beslissing
We kiezen optie 1.

- **`GET /api/ik`** (iedere ingelogde gebruiker, `isAuthenticated()`) geeft `{ "gebruikersnaam": "…", "beheerder": true|false }`, uit het token. In het contract `api/bank-api.yaml`.
- **De frontend** heeft één dienst die `/api/ik` opvraagt en onthoudt. Een guard op `/beheer` laat alleen een beheerder door; een klant gaat naar het overzicht.
- **De beveiliging blijft in bank-api.** De guard is alleen navigatie: wie hem omzeilt, krijgt van `/api/beheer/**` nog steeds een 403.

### In gewone woorden

De vraag is: hoe weet het scherm in de browser of je klant of beheerder bent?

- Na het inloggen bewaart de BFF je toegangsbewijs (het token van Keycloak) in je sessie. Daarin staat je rol. De browser krijgt het token nooit te zien.
- bank-api krijgt het token bij elk verzoek mee van de BFF, en weet dus wel wie je bent. De frontend weet het niet, en laat daarom nu iedereen het beheerscherm zien.

Daarom geven we de frontend een manier om te vragen "wie ben ik?":

1. **Een nieuw adres in bank-api: `GET /api/ik`.** Wie ingelogd is, krijgt een kort antwoord, bijvoorbeeld `{ "gebruikersnaam": "jan-4f2a-1", "beheerder": false }`. Voor een beheerder staat er `"beheerder": true`. bank-api haalt dat uit het token dat hij toch al krijgt.
2. **De frontend stelt die vraag één keer**, als de app geladen is, en onthoudt het antwoord.
3. **Bij het beheerscherm staat een portier** (een guard in Angular). Een beheerder mag door; een klant wordt naar zijn eigen overzicht gestuurd.

De portier is een bordje "verboden toegang", geen slot. Het echte slot zit in bank-api: een klant krijgt op `/api/beheer/**` altijd een 403, met of zonder portier. De portier zorgt er alleen voor dat een klant niet een leeg beheerscherm met een foutmelding ziet, maar netjes op zijn overzicht uitkomt.

## Gevolgen
- Eén verzoek extra na het laden van de app.
- De frontend kent de rol alleen voor de weergave; een beslissing over toegang hoort er niet bij.
- Het volgende scenario (de beheerder komt na het inloggen op het beheerscherm) gebruikt dezelfde dienst.

## Bewaking
- **Acceptatie:** "Klant kan niet bij beheer", in `rest` en `chromium`.
- **Controllertest** van `GET /api/ik`, tegen het contract: een klant en een beheerder, en zonder token een 401.
- **Vitest:** de guard stuurt een klant van `/beheer` naar het overzicht, en laat een beheerder door.
