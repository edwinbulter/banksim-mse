# 0016. CSRF-bescherming in de BFF met een `XSRF-TOKEN`-cookie

Datum: 2026-10-22
Status: Geaccepteerd

## Context
Slice 3. `PUT /api/beheer/simulatiedatum` is het eerste verzoek dat iets verandert. De browser stuurt het sessiecookie van de BFF automatisch mee (ADR 0008). Zonder bescherming kan een andere website dus namens een ingelogde beheerder de datum veranderen. De BFF heeft de standaard CSRF-bescherming van Spring Security aan, maar geeft het token nergens aan de browser. Daardoor krijgt nu iedereen 403 op een PUT, ook de beheerder. Het scenario "Een klant kan de simulatiedatum niet zetten" zou dan om de verkeerde reden groen zijn.

Opties:
1. **CSRF aan, met een cookie.** De BFF zet het token in het cookie `XSRF-TOKEN`, leesbaar voor JavaScript. Angular stuurt het automatisch terug als header `X-XSRF-TOKEN`. Spring Security noemt dit de configuratie voor een SPA. De bescherming zit in die header: een andere website kan het cookie niet lezen, en dus de header niet meesturen.
2. **CSRF uit voor `/api/**`**, en vertrouwen op `SameSite=Lax` van het sessiecookie. Dat is eenvoudiger, maar dan beschermt alleen de browser, en een fout in de cookie-instellingen opent het gat weer.

## Beslissing
- **BFF.** CSRF blijft aan, met de SPA-instelling van Spring Security (`csrf.spa()`, met `CookieCsrfTokenRepository`). Het token komt bij elk antwoord van de BFF mee als cookie `XSRF-TOKEN`. Op de installatie heeft het cookie `Path=/` en `Secure`, en geen `SameSite`; dat is gecontroleerd op 2026-10-22 met `curl -D -`.
- **Angular.** De ingebouwde XSRF-ondersteuning van `HttpClient` stuurt het token mee. Er is geen eigen code voor nodig.
- **e2e.** De REST driver, en de UI driver zolang er geen scherm is, lezen het cookie en sturen de header mee.
- **Geldig voor.** Alles wat geen GET, HEAD of OPTIONS is. Het inloggen loopt via redirects en is niet geraakt.

## Gevolgen
- Elk verzoek dat iets verandert, ook straks betalen en overschrijven, heeft het token nodig. De Angular-app doet dat vanzelf.
- Een 403 kan nu twee oorzaken hebben: geen CSRF-token, of geen rol. De tests onderscheiden ze. Het scenario voor de klant is pas betrouwbaar als de PUT van de beheerder lukt.

## Bewaking
- `BffTests`: een PUT zonder token geeft 403, en met het token uit het cookie komt hij door naar `bank-api`.
- Het scenario "Transacties na de simulatiedatum zijn onzichtbaar": de beheerder kan de datum alleen zetten met het token.
