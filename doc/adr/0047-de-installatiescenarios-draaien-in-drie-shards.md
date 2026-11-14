# 0047. De installatiescenario's draaien in CI in drie shards, elk met een eigen kind-cluster

Datum: 2026-11-11
Status: Geaccepteerd; de verdeling over de jobs is vervangen door ADR 0052

## Context
De pipeline duurde in run 43029153054 21:16. De job "Acceptance stage – installatiescenario's" bepaalde die duur: 18:36, waarvan 3:04 voor het kind-cluster en de installatie, 0:31 voor de browser, en 14:44 voor de 32 scenario's van het project `infra`. Die draaien na elkaar met één worker, omdat ze zelf installeren en verwijderen (`installatie.spec.ts`, `upgrade.spec.ts`), en omdat de `@simulatiedatum`-scenario's in `huishoudens.spec.ts` de datum van de hele bank verzetten (ADR 0014).

De andere jobs waren eerder klaar: de commit stage na 2:31, de acceptatietests na 9:15 en PIT na 13:17 (ADR 0032).

**Wat een shard is.** Met `--shard=1/3` draait Playwright het eerste derde deel van de scenario's. Elke shard is een eigen job op een eigen runner, met een eigen kind-cluster. De gedeelde toestand (de installatie en de simulatiedatum) is daardoor per shard gescheiden. Binnen een shard blijft één worker, dus daar verandert niets.

**Hoe Playwright verdeelt.** Zonder `fullyParallel` verdeelt `--shard` per bestand. `huishoudens.spec.ts` (25 scenario's, ongeveer 10:30) komt dan helemaal in één shard. Met `fullyParallel: true` verdeelt hij per scenario, op aantal en niet op duur. Een groep met `mode: 'serial'`, zoals `installatie.spec.ts`, blijft bij elkaar en in de volgorde van het bestand.

**Geschatte duur per shard,** met de tijden per scenario uit run 43029153054 en de verdeling van `--list`:

| | Shard 1 | Shard 2 | Shard 3 | Pipeline |
| --- | --- | --- | --- | --- |
| 2 shards | 16 scenario's, 5:57 | 16 scenario's, 8:45 | – | ongeveer 15:00 |
| 3 shards | 11 scenario's, 4:11 | 11 scenario's, 4:31 | 10 scenario's, 6:00 | ongeveer 13:20, gebonden aan PIT |

Shard 3 is de zwaarste: daarin zitten `installatie.spec.ts` (6 scenario's, samen ongeveer 4 minuten) en `upgrade.spec.ts`.

Opties:
1. **Niets veranderen.** 21 minuten.
2. **Per bestand verdelen**, zonder `fullyParallel`. `huishoudens.spec.ts` blijft één blok van ongeveer 10:30. Dat scheelt ongeveer 4 minuten.
3. **Twee shards, per scenario.** Ongeveer 15:00; shard 2 bepaalt de duur.
4. **Drie shards, per scenario.** Ongeveer 13:20; PIT bepaalt de duur.
5. **Vier of meer shards.** Levert niets op zolang PIT langer duurt dan een shard; elke shard kost wel een cluster.

## Beslissing
- In `e2e/playwright.config.ts` krijgt het project `infra` `fullyParallel: true`, met `workers: 1`.
- De job `installatie` in `.github/workflows/ci.yml` krijgt een matrix met `shard: [1, 2, 3]` en `fail-fast: false`. Elke shard draait `npm run test:infra -- --shard=<n>/3` en uploadt zijn eigen rapport (`playwright-rapport-infra-<n>`).
- De rapporten worden niet samengevoegd.

## Gevolgen
- **De pipeline duurt naar verwachting ongeveer 13:20** in plaats van 21:16. PIT is dan de langzaamste job. Een volgende versnelling begint daar (PIT per module in een eigen job).
- **Gemeten in CI** (run 43201996524, commit f04ff77, vergeleken met run 43029153054). De run is met de hand gestart via `workflow_dispatch`, omdat de run van de push (43095260090) tijdens een storing van GitHub Actions in de wachtrij bleef hangen.

  | | voor | na |
  | --- | --- | --- |
  | Pipeline | 21:16 | 11:41 |
  | Installatiescenario's, job / scenario's | 18:36 / 14:44 (32) | shard 1: 6:25 / 3:06 (11)<br>shard 2: 6:45 / 3:42 (11)<br>shard 3: 8:25 / 5:18 (10) |
  | Mutatietests (PIT) | 13:17 | 11:00 |
  | Acceptatietests | 6:40 | 7:42 |

  Alle 32 scenario's zijn geslaagd, ook die met `@destructief`. Anders dan verwacht is shard 3 de langzaamste job, en niet PIT: PIT was in deze run ruim 2 minuten sneller dan in run 43029153054. Shard 3 duurt 2:00 langer dan shard 1, precies op de grens uit Bewaking. De oorzaak is bekend: `installatie.spec.ts` zit als serial-groep van ongeveer 4 minuten in shard 3.
- **Drie keer de opzet van een cluster.** Elke shard maakt zijn eigen kind-cluster en installeert, ongeveer 3:35 per shard. Dat kost runnerminuten, geen tijd op de klok.
- **Elke shard heeft een eigen rapport.** Bij een fout zoek je het rapport van de shard waarin het falende scenario zat; de naam van de job zegt welke.
- **Een nieuw scenario kan de verdeling verschuiven.** Playwright verdeelt op aantal. Een nieuw scenario aan het eind van `huishoudens.spec.ts` schuift een ander scenario naar de volgende shard. Komt `installatie.spec.ts` met een andere zware groep in één shard, dan wordt die shard langer.
- **Lokaal verandert er niets.** `make e2e-infra` draait alles in één run met één worker. Met `fullyParallel: true` en één worker is de volgorde die van de bestanden.
- **Een scenario mag niet afhangen van een eerder scenario in een ander bestand of buiten zijn serial-groep.** Dat gold al: elk scenario in `huishoudens.spec.ts` begint met `omgeving.installeer()`. Met shards valt het op als dat ooit niet meer zo is, want dan draait het scenario op een ander cluster.

## Bewaking
- **actionlint** in de commit stage (`scripts/iac-check.sh`) controleert de matrix en de expressies in `ci.yml`.
- **De eerste push:** de duur van elke shard en van de hele pipeline vergelijken met run 43029153054. Het resultaat komt in deze ADR, onder Gevolgen. Duurt de pipeline langer dan 15 minuten, dan gaat deze beslissing terug naar de gebruiker.
- **Daarna:** de duur van de shards in de joblog. Is één shard meer dan 2 minuten langer dan de andere, dan is de verdeling uit balans en volgt een nieuwe ADR. Er is geen test die faalt als dat gebeurt.
