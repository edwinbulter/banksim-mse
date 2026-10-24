# 0021. Uitloggen beëindigt ook de Keycloak-sessie, via een antwoord dat de app zelf volgt

Datum: 2026-10-24
Status: Geaccepteerd

## Context
Scenario "Uitloggen" (S6.2, `e2e/specs/inloggen.spec.ts`): na het uitloggen ziet Jan "Inloggen", en "Inloggen" vraagt opnieuw om gebruikersnaam en wachtwoord.

Spring Security heeft in de BFF al een standaard-`POST /logout`. Die beëindigt alleen de sessie van de BFF en stuurt daarna door naar de login. Keycloak heeft zijn eigen sessie (SSO) nog, en logt Jan daarom direct weer in, zonder wachtwoord. Het scenario liet dat zien in het project `rest`: `Jan nog ingelogd, Expected: false, Received: true`. Daarmee faalt de story: een ander op hetzelfde apparaat kan zo bij Jans rekeningen.

OIDC heeft daarvoor RP-initiated logout. De BFF stuurt de browser naar het `end_session_endpoint` van Keycloak, met `id_token_hint` en `post_logout_redirect_uri`. Keycloak beëindigt dan zijn sessie en stuurt terug naar BankSim. De realm staat `https://<bank-host>/` al toe als `post.logout.redirect.uris` voor de client `bank-bff`.

De Angular-app roept de BFF aan met `HttpClient` (XHR). Een redirect op een XHR-verzoek volgt de browser zelf, buiten de app om. Een redirect naar een ander domein (Keycloak) loopt dan vast op CORS, en de pagina navigeert niet.

Opties:
1. **Alleen de BFF-sessie beëindigen** (wat er nu standaard gebeurt). Afgewezen: Keycloak logt stilletjes weer in, zoals de rode test laat zien.
2. **Een gewoon HTML-formulier** dat de browser naar `POST /logout` laat navigeren, met de redirects naar Keycloak. Het CSRF-token moet dan als formulierveld mee. `csrf.spa()` (ADR 0016) verwacht in een formulierveld een gemaskeerd token, en dat token kent de app niet. Dat zou een tweede CSRF-variant naast ADR 0016 opleveren.
3. **De BFF antwoordt met de URL, en de app volgt hem.**
   - `POST /logout` gaat zoals elke POST, met de header `X-XSRF-TOKEN` (ADR 0016);
   - de BFF beëindigt zijn sessie en antwoordt `202 Accepted` met de header `Location`: de URL van het `end_session_endpoint` van Keycloak, met `id_token_hint` en `post_logout_redirect_uri`;
   - de app zet `window.location` op die URL.
4. **Back-channel logout** (Keycloak meldt de BFF dat een sessie eindigt). Dat gaat over uitloggen elders, niet over deze knop. Het is hier niet nodig.

## Beslissing
We kiezen optie 3:
- een eigen `LogoutSuccessHandler` in de BFF berekent de URL met Spring's `OidcClientInitiatedLogoutSuccessHandler`, met als `post_logout_redirect_uri` de publieke URL uit `banksim.bff.public-url` plus `/`. Niet `{baseUrl}`: achter de ingress kent de BFF de publieke host daaruit niet zeker;
- hij antwoordt `202` met `Location`, in plaats van `302`;
- de knop "Uitloggen" in de kop van de app doet de POST en navigeert naar die `Location`.

## Gevolgen
- Na het uitloggen is ook de Keycloak-sessie weg. "Inloggen" toont weer het formulier.
- Eén CSRF-mechanisme blijft gelden voor elke POST (ADR 0016).
- De REST-driver doet hetzelfde als de app: hij POST, en volgt daarna zelf de `Location`.
- Keycloak toont geen bevestigingsscherm, omdat `id_token_hint` meegaat. Dat vereist dat het ID-token in de BFF-sessie staat, en dat doet `oauth2Login` al.
- Wie de app open heeft op een tweede tabblad, krijgt bij de volgende API-aanroep een 401 en ziet "Inloggen" (S1.1).

## Bewaking
- `BffTests`:
  - `POST /logout` met CSRF-token geeft `202`, met een `Location` naar het `end_session_endpoint`, met `id_token_hint` en `post_logout_redirect_uri`;
  - de sessie van de BFF is daarna ongeldig;
  - zonder CSRF-token geeft `POST /logout` een 403.
- Het scenario "Uitloggen", in rest, chromium, firefox en webkit (`@rook`): na het uitloggen vraagt "Inloggen" om het wachtwoord.
