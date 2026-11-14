# 0053. De Playwright-scenario's krijgen een Allure 2-rapport op GitHub Pages, met geschiedenis

Datum: 2026-11-14
Status: Geaccepteerd

## Context
De Playwright-scenario's draaien in CI in vier jobs: de acceptatietests en drie jobs met installatiescenario's (ADR 0052). Elke job bewaart zijn eigen HTML-rapport van Playwright als artifact (`playwright-rapport`, `playwright-rapport-infra-<deel>`). Daar ontbreken drie dingen:
- **één overzicht** van alle vier de jobs;
- **geschiedenis:** of een scenario eerder ook faalde, of het nieuw kapot is, en hoe de duur zich ontwikkelt;
- **ordening per slice of feature:** het rapport ordent alleen per bestand en per project.

Daarnaast is ervaring met Allure gevraagd: een rapportgenerator die veel teams gebruiken bij Playwright, met labels, stappen, bijlagen, categorieën van fouten en trends.

Opties:
1. **Alleen het HTML-rapport van Playwright.** Geen nieuwe tool, wel de trace viewer. De drie punten hierboven blijven ontbreken.
2. **Allure 3,** de nieuwe generator op Node (npm-pakket `allure`). Hij past bij de stack van `e2e/`, maar is nieuwer en minder verbreid.
3. **Allure 2,** de generator op Java (npm-pakket `allure-commandline`, dat de Java-CLI meebrengt). Die gebruiken de meeste teams nu. Hij heeft een JRE nodig.

Beide generatoren lezen dezelfde resultaten (`allure-results/`), geschreven door de reporter `allure-playwright`. Een latere overstap naar Allure 3 raakt dus alleen de generator.

## Beslissing
Optie 3, **naast** het HTML-rapport van Playwright. Dat blijft, vanwege de trace viewer.

- **Resultaten:** `allure-playwright` is een derde reporter in `e2e/playwright.config.ts`. De labels en parameters komen uit de DSL (`e2e/dsl/rapport.ts`, via de fixtures), niet uit de specs (ADR 0031).
- **Generator:** `allure-commandline`. De versies staan als devDependencies in `e2e/package.json`, vastgepind met de integrity in `package-lock.json`: `allure-playwright` en `allure-js-commons` 3.14.0, `allure-commandline` 2.46.1.
- **Publicatie:** een eigen workflow `.github/workflows/allure.yml` start na elke CI-run van een push naar `main` (`workflow_run`), ook na een rode run. Hij:
  - haalt de resultaten van de vier jobs op;
  - haalt de geschiedenis van de vorige versie op Pages;
  - maakt het rapport en zet het op GitHub Pages.

  De actions staan op een commit-SHA vast, zoals in `ci.yml`.
- **Lokaal:** `make allure` maakt het rapport van de laatste lokale run en opent het.

## Gevolgen
- **De pipeline wordt niet langer.** Het rapport komt in een eigen workflow, na de CI-run; groen of rood zie je even snel als nu. Het rapport staat ongeveer 1 tot 2 minuten later op Pages.
- **Het rapport is openbaar,** net als de repo. Er staat alleen testdata in. Tokens in de bijlagen worden gemaskeerd.
- **Pages moet één keer aan,** met als bron "GitHub Actions". Dat is een instelling van de repo en geen code.
- **De workflow `allure.yml` draait altijd in de versie die op `main` staat** (eigenschap van `workflow_run`). Een wijziging eraan is pas na de push te testen.
- **`retries: 0` blijft staan** (backlog fase 3.7). Allure herkent een flaky scenario daardoor alleen uit de geschiedenis, niet uit een herkansing.
- **Twee rapporten van dezelfde run:** het HTML-rapport van Playwright voor de trace viewer, en het Allure-rapport voor het overzicht.
- **Bij een overstap naar Allure 3** blijven de reporter en de labels; alleen de generator en de workflow veranderen.

## Bewaking
- `npm --prefix e2e run typecheck` en `scripts/lagen-check.sh`: de labels staan in de DSL, niet in de specs.
- `make iac` (actionlint) controleert `ci.yml` en `allure.yml`.
- **Na elke push:** de workflow `Allure-rapport` is groen. Het rapport op Pages telt evenveel scenario's als de acceptatiejob plus de installatiescenario's samen.
- **Meetbaar doel:** de CI-run wordt hoogstens 15 s langer dan de 9:28 van run 43616088400. Het rapport staat binnen 2 minuten na het einde van de CI-run op Pages. De meting komt hieronder, in een aparte commit.
