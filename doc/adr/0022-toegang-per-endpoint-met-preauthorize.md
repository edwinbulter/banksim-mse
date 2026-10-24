# 0022. Toegang per endpoint met `@PreAuthorize`, bewaakt door ArchUnit en een test over alle endpoints

Datum: 2026-10-24
Status: Geaccepteerd

## Context
Slice 6 vraagt om de fitness function "elke controller-methode heeft `@PreAuthorize` (deny by default)" (werkwijze §7).

`bank-api` regelt de toegang nu alleen met URL-regels in `BeveiligingConfig`:
- `/api/versie` voor iedereen;
- `/api/beheer/**` alleen met de realm-rol `beheerder` (ADR 0015);
- al het andere: ingelogd.

Er zijn vier controllers met vijf endpoints. Wie de rechten van een endpoint wil weten, zoekt in een andere klasse naar een URL-patroon.

Het risico zit in een volgend endpoint. Een nieuw endpoint voor de beheerder dat niet onder `/api/beheer/` valt, is voor elke ingelogde klant bereikbaar, en geen test merkt het. Slice 7 (betalen) en slice 10 (admin) brengen zulke endpoints. Eigenaarschap (andermans IBAN geeft 404) zit in de query's (S6.1) en blijft daar: dat is geen rol, maar data.

Opties:
1. **Alleen URL-regels** (nu), met slicetests per endpoint. Dat is eenvoudig en op één plek. De regel voor een nieuw endpoint hangt dan wel af van een padconventie, en niets dwingt af dat iemand erover nadenkt.
2. **`@PreAuthorize` op elke controller-methode**, met method security aan. De URL-laag houdt alleen een grof vangnet: `/api/versie` open en de rest ingelogd. ArchUnit eist dat elke methode met een `@…Mapping` een `@PreAuthorize` heeft, ook de open: `@PreAuthorize("permitAll()")`. Het recht staat dan naast de code, en een vergeten annotatie maakt de build rood.
3. **Allebei de rollen**: URL-regels én `@PreAuthorize` met dezelfde rollen. Dat is dubbele bescherming, maar de rol staat op twee plekken, en die gaan uit elkaar lopen.

## Beslissing
We kiezen optie 2:
- `@EnableMethodSecurity` in `bank-api`;
- elke controller-methode krijgt `@PreAuthorize`: `hasRole('beheerder')` voor de simulatiedatum, `isAuthenticated()` voor de rekeningen en transacties, en `permitAll()` voor de versie;
- `BeveiligingConfig` houdt `/api/versie` open en eist voor de rest alleen dat je bent ingelogd. De regel `/api/beheer/**` verhuist naar de annotaties.

## Gevolgen
- Het recht van een endpoint staat boven de methode. Een nieuw endpoint zonder `@PreAuthorize` maakt de commit stage rood.
- Een 403 komt voortaan uit de method security in plaats van uit de URL-regel. Voor de klant en de BFF verandert er niets: het blijft een 403.
- Eigenaarschap blijft in de query's, met de bestaande 404-tests (ADR 0011, S6.1).
- In een slicetest (`@WebMvcTest`) moet method security meedoen. Anders test je de annotatie niet.

## Bewaking
- **ArchUnit** (`architectuur/ToegangPerEndpointTests`): elke methode met `@GetMapping`, `@PostMapping`, `@PutMapping`, `@DeleteMapping`, `@PatchMapping` of `@RequestMapping` in `nl.banksim.api` heeft `@PreAuthorize`.
- **Een test over alle endpoints** (`beveiliging/AlleEndpointsTests`, de echte applicatie met PostgreSQL in Testcontainers):
  - hij leest de endpoints uit `RequestMappingHandlerMapping`, zodat een nieuw endpoint vanzelf meedoet;
  - zonder token geeft elk endpoint een 401, behalve de endpoints met `permitAll()`;
  - met een klant-token geeft elk endpoint met `hasRole('beheerder')` een 403.
- **De bestaande tests** blijven gelden: 403 voor een klant op de simulatiedatum (`SimulatiedatumControllerTests`) en 404 op andermans IBAN (`TransactieControllerTests`). Hetzelfde geldt voor het scenario "Een klant kan de simulatiedatum niet zetten".
