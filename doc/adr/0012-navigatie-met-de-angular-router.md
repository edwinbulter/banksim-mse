# 0012. Navigatie met de Angular-router, een URL per scherm

Datum: 2026-10-21
Status: Geaccepteerd

## Context
Slice 2a, S2a.1 (`e2e/specs/navigatie.spec.ts`). Het scenario "Herladen op de betaalrekening" eist dat de betaalrekening na herladen open blijft. Daarvoor heeft het scherm een eigen URL nodig. Nu houdt `App` in een signal bij welke rekening open is, en de URL blijft altijd `/`. Het scenario "Terug met de terugknop van de browser" eist een echte stap terug binnen de app, zonder dat de app opnieuw laadt. Nu laadt terug de startpagina `/` opnieuw, en de app begint dan weer bij het overzicht.

Na deze slice komen er schermen bij: Betalen (slice 7), Spaarrekening (8), Overschrijven (9) en Admin met rolafhankelijke routes (10, TO §1.2).

Opties:
1. **De Angular-router (`@angular/router`).** Een officieel package van Angular, met dezelfde versie als `@angular/core` (22.2.x). Routes zijn bijvoorbeeld `/` en `/rekeningen/:iban`. Met `withComponentInputBinding` wordt het IBAN uit de URL een input van de component. Guards voor de rollen van slice 10 zitten erin, en Vitest kan routes testen met `RouterTestingHarness`.
2. **Een eigen router op de History API** (`pushState` en `popstate`) in `App`. Er komt geen package bij, maar dan bouwen we zelf na wat de Angular-router al heeft: parameters, terugknop, links en later guards. Elke slice met een nieuw scherm breidt die eigen router uit.
3. **Geen URL per scherm** (zoals nu, of met `sessionStorage`). Herladen kan dan alleen met een omweg werken, en de terugknop verlaat het scherm niet netjes. Dit valt af op het herlaad-scenario.

Het IBAN komt in de URL, en daarmee in de browsergeschiedenis. Dat is geen nieuwe blootstelling: de API-paden bevatten het IBAN al (`/api/rekeningen/{iban}/transacties`). Andermans IBAN in de URL geeft niets, want de eigenaarscontrole zit in de query (404).

## Beslissing
- **Router.** We gebruiken de Angular-router (`@angular/router`), met dezelfde versie als de rest van Angular.
- **Routes.** `/` is het overzicht en `/rekeningen/:iban` de betaalrekening. Een onbekend pad gaat naar `/`.
- **Navigeren.** Navigeren voegt een stap toe aan de geschiedenis van de browser. We gebruiken geen `replaceUrl`, zodat de terugknop werkt.
- **Elk scherm laadt zijn eigen gegevens.** Het overzicht laadt de rekeningen, en de betaalrekening laadt de transacties aan de hand van het IBAN uit de URL. `App` houdt alleen de kop, de login-link en de voettekst.
- **Infrastructuur.** Er verandert niets. De nginx van `bank-web` stuurt een onbekend pad al naar `index.html` (`try_files`).

## Gevolgen
- Herladen, een bewaarde link en de terugknop werken per scherm.
- Elk nieuw scherm in latere slices is een route erbij. De rolafhankelijke routes van slice 10 worden guards op deze router, en geen nieuwe beslissing over navigatie.
- Er komt een package bij. Het wordt samen met Angular bijgewerkt, en een versieverschil met `@angular/core` breekt de build.
- `App` verandert: het laden van de gegevens verhuist naar een scherm per route (`OverzichtScherm`, `BetaalrekeningScherm`). `Overzicht` en `Betaalrekening` blijven ongewijzigd; het event `open` wordt in `OverzichtScherm` een navigatie. Een 401 meldt een interceptor aan `App`. De Vitest-tests van `App` gaan mee in dezelfde commit.
- De bestaande stappen van de e2e-drivers veranderen niet: ze klikken nog steeds op het IBAN en lezen het scherm.

## Bewaking
- De scenario's in `navigatie.spec.ts`, in chromium, firefox en webkit:
  - herladen houdt de betaalrekening open;
  - de terugknop toont het overzicht zonder dat de app opnieuw laadt. Een sabotage met `replaceUrl` maakt dit scenario rood. Zonder de eis "zonder opnieuw laden" bleef het groen, want terug kwam dan via een herstart van de app ook op het overzicht uit.
- Vitest (`app.spec.ts`, met de echte `appConfig`): `/` toont het overzicht, `/rekeningen/:iban` toont de betaalrekening van dat IBAN, en een onbekend pad gaat naar `/`.
- De versie: `@angular/router` eist via zijn peer-dependencies precies dezelfde versie als `@angular/core`, `@angular/common` en `@angular/platform-browser`. npm weigert een afwijkende versie (ERESOLVE), dus de commit stage faalt.
