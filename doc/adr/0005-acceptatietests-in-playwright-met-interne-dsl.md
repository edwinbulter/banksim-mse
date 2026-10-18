# 0005. Acceptatietests in Playwright Test met een interne DSL

Datum: 2026-10-17
Status: Geaccepteerd

## Context
De werkwijze stuurt elke slice met uitvoerbare voorbeelden (BDD) in het vierlaagse model: testcase, DSL, protocol drivers, systeem. Het origineel gebruikt Playwright Test in TypeScript in `e2e/`. Gherkin met Cucumber of playwright-bdd zou een extra tool aan de stack toevoegen.

## Beslissing
- Scenario's zijn Playwright-specs in `e2e/specs/`, met stappen `gegeven`, `als`, `dan`, `en` en `maar` uit de DSL (`e2e/dsl/`), die `test.step` gebruiken. Geen Gherkin.
- Twee protocol drivers achter dezelfde DSL: `RestDriver` (Playwright `APIRequestContext`, via de ingress) en `UiDriver` (page objects).
- Playwright-projecten: `rest` met de REST-driver, `chromium`, `firefox` en `webkit` met de UI-driver. Tags: `@wip` telt nergens mee, `@ui` alleen in de browsers.

## Gevolgen
- Geen nieuwe tool; dezelfde stack als het origineel.
- Elk scenario draait via de API en via drie browsers.
- Het project `rest` start technisch wel een browser (de fixture vraagt `page` aan), maar gebruikt hem niet.
- `.feature`-bestanden zijn later mogelijk met playwright-bdd op dezelfde DSL; dat is dan een nieuwe ADR.

## Bewaking
- `npm --prefix e2e run typecheck` in de commit stage.
- Code review: specs bevatten geen selectors, URL's of JSON; die horen in de drivers.
