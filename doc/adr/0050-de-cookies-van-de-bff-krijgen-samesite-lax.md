# 0050. De cookies van de BFF krijgen `SameSite=Lax`

Datum: 2026-11-13
Status: Geaccepteerd

## Context
CFR OWASP Top 10, los backlogpunt "Scenario over de sessie-cookies". De BFF zet twee cookies (ADR 0008, 0016). Via de echte ingress zag het er op 2026-11-13 zo uit (`curl -D -` op `GET /oauth2/authorization/keycloak`):

```
set-cookie: XSRF-TOKEN=…; Path=/; Secure
set-cookie: JSESSIONID=…; Path=/; Secure; HttpOnly
```

`Secure` en `HttpOnly` waren er, maar zonder test. `SameSite` ontbrak op beide. Wat een browser dan doet, verschilt per browser: Chromium behandelt een cookie zonder `SameSite` als `Lax`, andere browsers sturen het ook mee bij een POST vanaf een andere site.

`SameSite` bepaalt wanneer de browser een cookie meestuurt bij een verzoek dat van een andere site komt:
- **`Strict`:** nooit, ook niet bij een gewone link of een doorverwijzing;
- **`Lax`:** alleen bij een navigatie met GET in het hoofdvenster, zoals een link of de terugkeer van Keycloak na het inloggen; niet bij een POST, een formulier, een iframe of `fetch` vanaf een andere site;
- **`None`:** altijd (alleen samen met `Secure`).

Keycloak (`auth-mse.localtest.me`) en de bank (`bank-mse.localtest.me`) vallen allebei onder `localtest.me` en zijn dus één site. In een andere installatie hoeft dat niet zo te zijn.

Opties:
1. **Niet zetten.** Het gedrag hangt af van de browser.
2. **`Lax` op beide cookies.** De terugkeer van Keycloak naar `/login/oauth2/code/keycloak` is een GET-navigatie, dus het sessiecookie met het opgeslagen inlogverzoek gaat mee. Een formulier op een andere site dat een POST naar de bank doet, krijgt de sessie niet mee: een tweede verdediging naast het CSRF-token van ADR 0016.
3. **`Strict` op beide cookies.** Lokaal werkt het inloggen, omdat Keycloak en de bank één site zijn. Staat Keycloak op een andere site, dan mist de terugkeer na het inloggen het sessiecookie en mislukt het inloggen. Een link naar de bank in een e-mail opent dan een uitgelogde bank.
4. **`None`.** Geen extra bescherming; alleen nodig als de bank in een iframe op een andere site draait.

## Beslissing
We kiezen optie 2. Beide cookies van de BFF krijgen `SameSite=Lax`:
- het sessiecookie `JSESSIONID` met `server.servlet.session.cookie.same-site: lax` in `backend/bank-bff/src/main/resources/application.yaml`;
- het cookie `XSRF-TOKEN` met een `CookieCsrfTokenRepository` met een cookie customizer in `BeveiligingConfig`, naast de SPA-instelling van ADR 0016.

`Secure` en `HttpOnly` blijven zoals ze zijn: `Secure` op beide, `HttpOnly` alleen op het sessiecookie. Angular moet `XSRF-TOKEN` kunnen lezen (ADR 0016).

## Gevolgen
- **Elke browser doet hetzelfde,** in plaats van wat hij standaard doet bij een cookie zonder `SameSite`.
- **Een POST vanaf een andere site krijgt de sessie niet mee.** Het CSRF-token blijft nodig: een andere pagina op dezelfde site (`*.localtest.me`) valt niet onder `SameSite`.
- **Het inloggen blijft werken,** ook als Keycloak op een andere site komt te staan.
- **De bank kan niet in een iframe op een andere site draaien** met een sessie. Dat is ook niet de bedoeling.

## Bewaking
- **Installatiescenario's in `e2e/specs/infra/cookies.spec.ts`:**
  - elk `JSESSIONID` en elk `XSRF-TOKEN` dat de bank tijdens het inloggen zet, door de echte ingress, heeft de afgesproken vlaggen;
  - in een echte browser ziet een script op de pagina `XSRF-TOKEN`, maar niet `JSESSIONID`.
- **`BffTests`:** het cookie `XSRF-TOKEN` heeft `SameSite=Lax`. **`SessiecookieTests`** (met een echte Tomcat, want MockMvc zet geen sessiecookie): het sessiecookie heeft `SameSite=Lax`.
