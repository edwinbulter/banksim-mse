# 0051. De BFF bewaart de tokens in de sessie

Datum: 2026-11-14
Status: Geaccepteerd

## Context
CFR OWASP Top 10, los backlogpunt "Uitloggen wist de tokens van de klant niet uit het geheugen van de BFF".

ADR 0008 noemt bij de Gevolgen dat de access en refresh tokens in de `Map` van `InMemoryOAuth2AuthorizedClientService` staan, per gebruiker en niet per sessie. Dat is de standaard van Spring Boot. Daar komen drie problemen uit, gezien met `TokensPerSessieTests` (rood op 2026-11-14):

1. **Een tweede login overschrijft de tokens van de eerste sessie.** Jan logt in op zijn laptop en daarna op zijn telefoon. Daarna gebruikt ook de laptop de tokens van de telefoon (`expected: "access-laptop", but was: "access-telefoon"`).
2. **Uitloggen laat de tokens staan.** De sessie wordt ongeldig, maar de tokens blijven in het geheugen tot de volgende login van Jan of een herstart.
3. **Uitloggen op het ene apparaat breekt het andere, na maximaal 5 minuten.** Bij het uitloggen beëindigt Keycloak de sessie van de telefoon (ADR 0021), en weigert daarna het refresh token van de telefoon. Verloopt het access token van de laptop (na 300 s, `accessTokenLifespan`), dan ververst de BFF met dat refresh token en krijgt `invalid_grant`. Het verzoek van de laptop mislukt.

Het scenario "Uitloggen op het ene apparaat laat Jan op het andere ingelogd" (`inloggen.spec.ts`) is groen, omdat het niet 5 minuten wacht. Het bewaakt dat een oplossing de laptop niet meteen uitlogt.

Opties:
1. **Niets veranderen.** De drie problemen blijven.
2. **Een `LogoutHandler` die de tokens van de gebruiker wist.** Lost probleem 2 op, maar de opslag blijft per gebruiker: de laptop verliest bij het uitloggen op de telefoon meteen zijn tokens (de laptop is dan uitgelogd, het scenario wordt rood). Probleem 1 en 3 blijven.
3. **De tokens in de sessie, met `HttpSessionOAuth2AuthorizedClientRepository`.** Dat is een klasse van Spring Security, geen nieuwe library. Elke sessie houdt zijn eigen tokens, en die verdwijnen met de sessie: bij het uitloggen, of als de sessie verloopt.

## Beslissing
We kiezen optie 3. `BeveiligingConfig` krijgt een bean `OAuth2AuthorizedClientRepository` van het type `HttpSessionOAuth2AuthorizedClientRepository`. De login (`oauth2Login`) en het doorsturen naar bank-api (`tokenRelay()`) gebruiken die bean al; aan de route verandert niets.

## Gevolgen
- **Elke sessie heeft zijn eigen tokens.** Een tweede apparaat raakt het eerste niet, ook niet bij het verversen.
- **Uitloggen wist de tokens van die sessie,** omdat de sessie ongeldig wordt. Een sessie die verloopt (in Tomcat standaard na 30 minuten zonder verzoek) neemt haar tokens mee; de `Map` per gebruiker groeide tot een herstart.
- **De sessie en de tokens staan op één plek.** Een sessie in PostgreSQL (CFR Resilience, ADR 0008) neemt de tokens dus vanzelf mee.
- De passage in de Gevolgen van ADR 0008 over `InMemoryOAuth2AuthorizedClientService` geldt niet meer.
- De bank blijft één replica houden: de sessie staat nog in het geheugen van de pod (ADR 0008).

## Bewaking
- **`TokensPerSessieTests`:** een tweede login van Jan laat de tokens van zijn eerste sessie staan; na het uitloggen zijn de tokens van die sessie weg en houdt de andere sessie de hare; na het uitloggen op de telefoon ververst de laptop met zijn eigen refresh token.
- **Scenario "Uitloggen op het ene apparaat laat Jan op het andere ingelogd"** (`e2e/specs/inloggen.spec.ts`, `rest` en `chromium`): de laptop blijft na het uitloggen op de telefoon ingelogd, door de echte BFF en Keycloak. De REST-driver volgt bij het overzicht geen doorverwijzingen, zoals de app: zonder tokens stuurt de BFF een verzoek naar Keycloak, en de request-context van Playwright zou daar stilletjes opnieuw inloggen.
