# 0019. Firefox en webkit draaien alleen de scenario's met `@ui` of `@rook`

Datum: 2026-10-24
Status: Geaccepteerd

## Context
Elk scenario draait nu in vier projecten: `rest`, `chromium`, `firefox` en `webkit` (ADR 0005). Scenario's met `@simulatiedatum` draaien daarna in een keten van vier projecten, één voor één (ADR 0014). De acceptance stage duurde daardoor 12,9 minuten (run 40083439857), en dat groeit met elke slice.

Gemeten in run 40030250651:
- firefox en webkit kostten samen 577 s van de 1250 s testtijd, plus 113 s in de keten;
- de keten draait helemaal serieel, met 216 s wandklok.

De domeinregels (saldo, filters, de datum van de bank, eigenaarschap) zitten in de backend. Ze zijn dus voor elke browser gelijk. `rest` bewijst ze via de API en `chromium` via het scherm.

Wat firefox en webkit wel extra vangen:
- verschillen in rendering en invoer, zoals formulieren, focus en de terugknop;
- het inloggen bij Keycloak in die browser. Daar was webkit eerder flaky (zie de losse backlogpunten in werkwijze §7).

Opties:
1. **Alles in alle browsers** (nu). Dat geeft de meeste dekking, maar de pipeline wordt per slice trager.
2. **Firefox en webkit alleen voor `@ui` en `@rook`.** `@ui` zijn de scenario's over het scherm zelf: navigatie, het zoekformulier en straks een verkeerd wachtwoord. `@rook` is een kleine set die het hele pad door elke browser bewijst: de startpagina met de versie, en het inloggen tot het overzicht met het saldo.
3. **Firefox en webkit alleen nachtelijk, met alles.** Dan houd je de dekking, maar een browserfout komt pas de volgende dag boven. Dat geeft een tweede pipeline om te onderhouden.

## Beslissing
We kiezen optie 2:
- `rest` en `chromium` draaien alle scenario's;
- `firefox` en `webkit` draaien alleen de scenario's met de tag `@ui` of `@rook`;
- `@rook` staat op `skelet.spec.ts` en `overzicht.spec.ts`;
- de keten van ADR 0014 wordt `simulatiedatum-rest` → `simulatiedatum-chromium`. De beslissing van 0014 (serieel, na alle andere) blijft staan.

## Gevolgen
- De acceptatietests worden naar schatting ~45% korter, en de keten wordt ongeveer half zo lang.
- **Een scenario dat iets aan het scherm toevoegt**, zoals een nieuw formulier of nieuwe navigatie, hoort `@ui` te krijgen, of een eigen `@ui`-scenario. Anders ziet alleen chromium het. Dat is een regel voor wie scenario's schrijft. Bij de ⛔ van een scenario hoort de vraag: geldt dit voor alle browsers?
- Een browserfout in een scherm zonder `@ui`-scenario vinden we pas in de verkennende sessie (werkwijze §10.1), of als iemand hem meldt.
- `make e2e` lokaal draait dezelfde verdeling.

## Bewaking
- `playwright.config.ts`: `firefox` en `webkit` hebben `grep: /@ui|@rook/`; de keten heeft alleen `simulatiedatum-rest` en `simulatiedatum-chromium`.
- `npx playwright test --list` toont per project het aantal scenario's. In de joblog van de acceptance stage staat het totaal (`Running N tests`). Een onverwachte daling valt op bij de ⛔ van de commit.
