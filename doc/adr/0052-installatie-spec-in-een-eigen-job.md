# 0052. `installatie.spec.ts` draait in CI in een eigen job; de andere installatiescenario's in twee shards

Datum: 2026-11-14
Status: Geaccepteerd

Vervangt ADR 0047 voor de verdeling over de jobs. Dat elke job een eigen runner en een eigen kind-cluster heeft, blijft uit ADR 0047.

## Context
ADR 0047 verdeelt de installatiescenario's met `--shard=<n>/3` over drie jobs. Hoe Playwright 1.63 dat doet, staat in `filterForShard` (`playwright/lib/runner/index.js`):
- op **aantal** scenario's, niet op duur: bij 43 scenario's 15, 14 en 14, in de volgorde van de bestanden;
- een groep met `mode: 'serial'` blijft bij elkaar en gaat helemaal naar de shard waarin zijn **eerste** scenario valt. `installatie.spec.ts` is zo'n groep: 6 scenario's die samen ongeveer 3:30 duren.

Of die groep netjes op een grens valt, hangt dus af van het aantal scenario's in de bestanden ervoor. In run 43606433077 kwam er één scenario bij in `keycloak.spec.ts`, en kreeg één shard 19 scenario's, de andere 9 en 15. De zwaarste job duurde 9:36 in plaats van ongeveer 7:00, en de pipeline 11:13 in plaats van 8:27.

Gemeten duur van de scenario's in die run, zonder de ongeveer 3:20 per job voor het cluster en de installatie:

| Bestand | Scenario's | Duur |
| --- | --- | --- |
| `huishoudens.spec.ts` | 25 | 260 s |
| `installatie.spec.ts` (serial) | 6 | 209 s |
| `upgrade.spec.ts` | 1 | 33 s |
| `cookies`, `tokens`, `keycloak` | 11 | 43 s |
| **Totaal** | 43 | 545 s |

Opties:
1. **Niets veranderen.** De verdeling blijft afhangen van het toeval, en elk nieuw scenario kan de pipeline minuten langer maken.
2. **Gewichten per shard,** met `PWTEST_SHARD_WEIGHTS`. Dat is een interne variabele van Playwright, niet gedocumenteerd; hij kan bij een update verdwijnen. Het blijft verdelen op aantal.
3. **`installatie.spec.ts` hernoemen,** zodat hij vooraan staat en altijd in shard 1 begint. Ook dat blijft op aantal: de rest van shard 1 hangt af van de bestanden erna.
4. **`installatie.spec.ts` in een eigen job, de andere bestanden in twee shards.** Daarin zit geen serial-groep meer, dus verdelen op aantal ligt dicht bij verdelen op duur. Verwacht, met de tijden hierboven: 209 s, 155 s en 181 s.

## Beslissing
Optie 4. De job `installatie` in `.github/workflows/ci.yml` krijgt een matrix `deel: [installatie, overig-1, overig-2]`:
- `installatie`: `npm run test:infra -- specs/infra/installatie.spec.ts`;
- `overig-1` en `overig-2`: `npm run test:infra -- --grep-invert 'installatie\.spec\.ts' --shard=<n>/2`. Playwright vergelijkt `--grep` ook met de bestandsnaam.

Een nieuw bestand komt zo vanzelf in `overig`; niemand hoeft het toe te wijzen. Het rapport heet `playwright-rapport-infra-<deel>`.

## Gevolgen
- **De zwaarste job wordt `installatie`, met ongeveer 3:30 aan scenario's.** Naar verwachting duurt hij ongeveer 6:50, en de pipeline ongeveer 8:30, ongeveer even lang als de acceptatietests.
- **Groeit `installatie.spec.ts`, dan groeit die job mee.** Hij is niet te verdelen, omdat de scenario's op elkaar voortbouwen.
- **Groeien de andere bestanden, dan verdelen ze zich over twee jobs.** Komt er weer een serial-groep bij, dan geldt hetzelfde probleem als hierboven.
- De regel uit ADR 0047 blijft: een scenario mag niet afhangen van een scenario in een ander bestand.

## Bewaking
- `actionlint` controleert de workflow.
- Vooraf lokaal met `--list` en dezelfde selectie als in CI: 6, 19 en 18 scenario's, samen de 43 van het project `infra`, zonder dubbele.
- **Meetbaar doel:** in de eerste run na deze wijziging duurt de zwaarste installatiejob 7:00 of korter, en de pipeline ongeveer 8:30 of korter. De meting komt hieronder, in een aparte commit.
