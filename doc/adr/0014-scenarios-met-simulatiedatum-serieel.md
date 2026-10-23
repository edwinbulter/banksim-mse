# 0014. Scenario's met `@simulatiedatum` draaien serieel, na alle andere

Datum: 2026-10-22
Status: Geaccepteerd

## Context
Slice 3 (`e2e/specs/simulatiedatum.spec.ts`). De simulatiedatum is één waarde voor de hele bank (ADR 0013). Aliassen isoleren scenario's van elkaar (ADR 0003), maar niet van deze gedeelde toestand. Draait een scenario met 5 oktober 2026 tegelijk met "Toon meer" of een scenario met een transactie van vandaag, dan ziet dat andere scenario de verkeerde dag.

Het gaat niet alleen om scenario's binnen één project. `rest`, `chromium`, `firefox` en `webkit` draaien tegelijk.

Opties:
1. **Een keten van eigen projecten.** `simulatiedatum-rest` → `simulatiedatum-chromium` → `simulatiedatum-firefox` → `simulatiedatum-webkit`, elk met één worker, en de eerste pas na de gewone projecten (`dependencies`). De gewone projecten slaan `@simulatiedatum` over.
2. **De hele testrun met één worker**, zoals het origineel (TO §14). Dat is eenvoudig, maar de 29 andere scenario's worden dan veel trager.
3. **Een simulatiedatum per klant.** Dat isoleert wel, maar het is een ander domeinmodel dan het FO: de beheerder zet de datum voor de hele bank.

## Beslissing
- **Keten.** We kiezen optie 1. `npm test` (en dus `make e2e`) draait de vier gewone projecten en de keten.
- **Opruimen.** Na elk scenario met `@simulatiedatum` wist de fixture de datum, ook als het scenario faalde. Dat doet de beheerder, met zijn eigen sessie. Wissen, niet op vandaag zetten: anders bevriest de bank op de testdag, en verdwijnen transacties van morgen na middernacht.

## Gevolgen
- De scenario's met `@simulatiedatum` starten pas als de andere klaar zijn. Faalt er een gewoon scenario, dan slaat Playwright de keten over.
- Eén scenario draaien: `npx playwright test --project=simulatiedatum-rest --no-deps specs/simulatiedatum.spec.ts`. Zonder `--no-deps` draaien eerst alle gewone projecten.
- Elk scenario met `@simulatiedatum` maakt in de opruimstap een beheerder aan. Dat kost een seed-pod extra.
- In CI geldt `retries: 1` ook binnen de keten. Een herkansing draait na het opruimen van de vorige poging.

## Bewaking
- `playwright.config.ts`: de gewone projecten hebben `@simulatiedatum` in `grepInvert`; de keten heeft `workers: 1` en `dependencies`. `npx playwright test --list` toont de scenario's alleen in de keten.
- Een scenario dat de datum laat staan, laat de gewone scenario's in de volgende run falen. Het saldo en de transacties van vandaag kloppen dan niet meer.
