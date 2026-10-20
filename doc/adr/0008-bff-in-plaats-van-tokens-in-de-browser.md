# 0008. Een BFF in plaats van tokens in de browser

Datum: 2026-10-20
Status: Geaccepteerd

## Context
Met Keycloak (ADR 0007) moet de browser op de een of andere manier namens de klant de API aanroepen. Het origineel gebruikt `bank-bff`: Spring Cloud Gateway als OIDC-client, met een sessiecookie in de browser. Dit staat in de ADR-backlog van de werkwijze (hoofdstuk 6).

Opties:
1. **BFF.** De BFF doet de OIDC-flow (authorization code met PKCE), bewaart de tokens server-side en geeft de browser alleen een `HttpOnly`-sessiecookie. De BFF stuurt verzoeken naar `bank-api` door met het access token.
2. **Tokens in de browser.** Angular doet de OIDC-flow zelf en stuurt het token mee. Eenvoudiger, maar tokens zijn bereikbaar voor JavaScript (XSS-risico), en het wijkt af van het origineel.

## Beslissing
Optie 1. Een nieuwe module `bank-bff` op Spring Cloud Gateway (zelfde aanpak als het origineel). De ingress stuurt `/api`, `/oauth2`, `/login` en `/logout` naar `bank-bff`, en niet meer rechtstreeks naar `bank-api`. `bank-api` wordt een OAuth2 resource server die alleen JWT's van de realm `banksim-mse` accepteert. De sessie staat voorlopig in het geheugen van de BFF, met één replica. Een sessie in PostgreSQL volgt in een eigen ADR, zodra er meer replica's komen (CFR resilience).

## Gevolgen
- De browser ziet nooit een token; CSRF-bescherming is nodig voor muterende verzoeken (vanaf betalen, slice 7).
- De REST-driver moet ook via de BFF: hij doorloopt de inlogflow met cookies, net als de browser.
- `GET /api/versie` gaat via de BFF en blijft zonder login bereikbaar, zodat het skeletscenario blijft werken.

## Bewaking
- helm-unittest: de Ingress stuurt `/api` naar `bank-bff`, niet naar `bank-api`.
- Slicetests in `bank-api`: zonder token 401 (`RekeningControllerTests`). Met een echt ondertekend token van een andere realm, met een andere audience of met een andere sleutel ook 401 (`TokenControleTests`).
- `BffTests`: zonder sessie 401 op `/api/**`. Inloggen gaat met PKCE via de publieke Keycloak-URL. `/api/versie` blijft open. Na het inloggen gaat het access token mee naar `bank-api`, de cookies niet.
- Acceptatiescenario: het overzicht is alleen na inloggen zichtbaar (beide drivers loggen in via de BFF).
