# 0034. Contract-first OpenAPI voor bank-api

Datum: 2026-11-01
Status: Geaccepteerd

## Context
Op de grens tussen de frontend en bank-api test elke kant tegen zijn eigen aanname van de andere kant (backlog fase 6.2):
- de Vitest-tests van de frontend verzinnen zelf de JSON van de API;
- de interfaces in de frontend, zoals `Rekening` en `Transactie`, zijn met de hand geschreven;
- de controllertests van bank-api controleren losse velden met `jsonPath`.

Een breuk, zoals een veld dat anders heet, ziet nu alleen de acceptance stage. De ADR-backlog (werkwijze hoofdstuk 6) had "Contract-first OpenAPI, gegenereerde Angular-client" uitgesteld tot de frontend de API vaker aanroept. Dat is nu zo: 9 operaties, 9 schermen. Hoe later de overstap, hoe meer endpoints en interfaces er achteraf beschreven en vervangen moeten worden.

**Spike op 2026-11-01**, met `/api/versie`:
- **Java:** `swagger-request-validator-mockmvc` 2.44.9 (Atlassian, Apache 2.0) werkt met Spring Boot 4 en MockMvc. Hij controleert per verzoek in een bestaande controllertest het verzoek én het antwoord tegen de specificatie. Een verkeerd type gaf: `[Path '/versie'] Instance type (string) does not match any allowed primitive type (allowed: ["integer"])`.
- **Valkuil:** met `openapi: 3.1.0` slaat de validator de schema's **stil over**; de test bleef groen met een fout antwoord. Met `3.0.3` werkt het.
- **TypeScript:** `openapi-typescript` 7.13.0 maakt uit dezelfde specificatie de types, zonder code die in de app meedraait.
- De validator brengt `jakarta.validation` mee op het test-classpath; Spring meldt dan in de testlog dat er geen validatieprovider is. Alleen een INFO-melding.

Opties:
1. **Contract-first: één specificatie met de hand, beide kanten ertegen.** bank-api wordt in zijn controllertests ertegen gecontroleerd; de frontend krijgt er de types uit.
2. **Code-first: de specificatie genereren uit bank-api** (springdoc). Geen dubbel werk, maar de specificatie volgt dan de code, ook een vergissing; ze is geen afspraak meer. Ook een extra library in de app zelf.
3. **Voorbeeldantwoorden in een gedeelde map**, zonder tool. Controleert het echte antwoord tot op het teken, maar geeft de frontend geen types en dekt maar één variant per endpoint (zie backlog fase 6.2).
4. **Pact (consumer-driven).** Zwaarder: pact-js en pact-jvm, en een uitwisseling van contracten.

## Beslissing
We kiezen optie 1.

- **De specificatie** staat in `api/bank-api.yaml`, OpenAPI **3.0.3**, met de hand geschreven. Ze beschrijft de 9 operaties van bank-api onder `/api/**`: paden, parameters, verzoeken, antwoorden en de probleemberichten (RFC 9457). Bedragen zijn `type: string` met een patroon (ADR 0010). Objecten hebben `additionalProperties: false`, zodat een extra veld ook opvalt. De routes van de BFF zelf (`/oauth2`, `/login`, `/logout`) horen er niet bij.
- **bank-api:** elke controllertest controleert elk antwoord tegen de specificatie, via één gedeelde instelling van MockMvc (`ApiContract`, met `alwaysExpect`). Een aparte test controleert dat elk endpoint van de applicatie in de specificatie staat, en andersom, zoals `AlleEndpointsTests` dat voor de rechten doet.
  - **Alleen de antwoorden, niet de verzoeken.** De controllertests sturen met opzet ook ongeldige verzoeken, zoals een bedrag "25.001", om de 400 te testen. De vorm van een geldig verzoek bewaakt de typecheck van de frontend, die het verzoek uit de specificatie typt.
  - **Een foutantwoord (4xx) mag zonder body.** Spring Security en `ResponseStatusException` geven in MockMvc geen body. Komt er wel een body, dan moet hij kloppen.
- **Frontend:** `openapi-typescript` maakt `frontend/src/app/api/bank-api.d.ts`. Dat bestand staat in Git. De handgeschreven interfaces gaan weg; schermen en Vitest-tests gebruiken de gegenereerde types (`src/app/api/schemas.ts`). Geen gegenereerde Angular-client: `HttpClient` blijft, met de types erbij.
  - **De generator staat in `api/`**, met een eigen `package.json` en lockfile, niet als devDependency van de frontend. `openapi-typescript` 7.13.0 vraagt TypeScript 5 als peer; de frontend gebruikt TypeScript 6. In `api/` heeft hij zijn eigen TypeScript 5, zonder `--legacy-peer-deps` en zonder dat de app ervan afhangt.
- **Commit stage:** de frontendjob genereert de types opnieuw en faalt als het resultaat afwijkt van wat in Git staat. Zo kan de specificatie niet veranderen zonder dat de frontend meeverandert.

## Gevolgen
- Een wijziging aan de API begint in `api/bank-api.yaml`. Dan faalt de controllertest van bank-api tot de code klopt, en faalt de typecheck van de frontend waar die het oude veld gebruikt.
- Twee nieuwe tools, alleen in tests en bij het bouwen: de validator in bank-api (test-scope) en `openapi-typescript` in `api/`. Geen van beide draait in de app. Ondersteunt een latere versie TypeScript 6, dan kan de generator naar de frontend; dat is geen nieuwe beslissing.
- `make frontend-test` genereert de types eerst opnieuw (`npm --prefix api run types`); lint slaat het gegenereerde bestand over (`eslint.config.js`), want niemand schrijft het met de hand.
- De specificatie is ook de documentatie van de API. `doc/story-door-de-stack.md` noemt haar als bron van de waarheid voor de vorm van de API.
- De validator ondersteunt OpenAPI 3.1 niet volledig; een upgrade naar 3.1 vraagt eerst een spike, en een sabotage die rood moet worden.
- De e2e-drivers houden voorlopig hun eigen types; een volgende stap kan ze ook uit de specificatie halen.

## Bewaking
- **Controllertests van bank-api** met de validator: sabotage met een verkeerd type in de specificatie moet rood worden.
- **De test "elk endpoint staat in de specificatie":** een endpoint zonder beschrijving moet rood worden.
- **De frontendjob:** types genereren en `git diff --exit-code`; een wijziging in de specificatie zonder nieuwe types moet rood worden.
- **De typecheck van de frontend** (`strict`, backlog fase 2.6): een veld dat niet in de specificatie staat, geeft een compileerfout.
