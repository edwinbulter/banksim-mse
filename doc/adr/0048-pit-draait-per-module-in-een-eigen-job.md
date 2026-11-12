# 0048. PIT draait per module in een eigen job, bank-api in twee delen

Datum: 2026-11-12
Status: Geaccepteerd

## Context
De job "Mutatietests (PIT)" (ADR 0018, ADR 0032) was één Maven-commando voor de hele backend. Maven doet de modules dan na elkaar, op één runner, en de tijden tellen op. Twee runs met precies dezelfde code en dezelfde mutanten:

| Module | Run 43201996524 | Run 43211426152 |
| --- | --- | --- |
| bank-domain | 0:15 | 0:21 |
| bank-api | 6:37 | 9:36 |
| bank-bff | 0:23 | 0:35 |
| bank-seed | 3:18 | 5:05 |
| **Job** | **11:00** | **16:00** |

Het verschil komt door de runner: in de tweede run was elke module ongeveer 45% langzamer. In die run bepaalde PIT de duur van de pipeline (16:05), terwijl de installatiescenario's (ADR 0047) al na 10 minuten klaar waren. De grens van de job is 20 minuten.

bank-api is het grootste deel: 220 mutanten. Daarvan zitten 69 in `transactie` en 49 in `rente`; de rest is verdeeld over `betaling` (30), `rekening` (23), `simulatie` (18) en zes kleine packages (31).

Opties:
1. **Zo laten.** De pipeline duurt 11 tot 16 minuten, afhankelijk van de runner.
2. **Per module een eigen job.** De duur wordt die van bank-api alleen, met de opzet van een job erbij: ongeveer 7:30 tot 10:30.
3. **Per module een eigen job, en bank-api in twee delen.** Elk deel meet de dekking opnieuw, maar muteert maar de helft.
4. **Een grotere runner** met meer cores en meer `threads`. Dat kost geld, en een trage runner blijft traag.

## Beslissing
- De job `mutatietests` in `.github/workflows/ci.yml` krijgt een matrix met vier jobs die tegelijk draaien:
  - bank-api, deel `transactie` en `rente`;
  - bank-api, de rest;
  - bank-seed;
  - bank-domain met bank-bff.
- Elke job draait `mvnw -pl <modules> -am [-P <profiel>] test-compile org.pitest:pitest-maven:mutationCoverage`, en uploadt een eigen rapport (`pit-rapport-<deel>`).
- `backend/bank-api/pom.xml` krijgt de profielen `pit-transactie-rente` en `pit-rest`:
  - `pit-transactie-rente` zet `targetClasses` op `nl.banksim.api.transactie.*` en `nl.banksim.api.rente.*`;
  - `pit-rest` voegt die twee packages toe aan `excludedClasses`.
- `make mutatie` blijft gelijk: lokaal draait PIT de hele backend in één keer, zonder profiel.

## Gevolgen
- **Er valt geen package weg.** `pit-rest` neemt alles behalve de packages van het eerste deel, dus een nieuw package valt vanzelf in `pit-rest`. Valt een profiel weg, dan draait die job heel bank-api: dubbel werk, maar er wordt niets overgeslagen.
- **De drempel van 95% (ADR 0018) gaat voor bank-api per deel gelden.** Dat is strenger dan per module: een deel met veel levende mutanten kan niet meer worden gecompenseerd door het andere deel.
- **Het meten van de dekking gebeurt twee keer** voor bank-api, ongeveer 50 seconden per deel. Door `-am` muteert elke job ook bank-domain, ongeveer 20 seconden. Dat kost runnerminuten, maar geen tijd op de klok.
- **Vier rapporten in plaats van één.** Een levende mutant zoek je in het rapport van het deel waar zijn package in zit.
- **Lokaal gemeten op 2026-11-12:**

  | Deel | Mutanten | Dekking meten | Muteren | bank-api totaal |
  | --- | --- | --- | --- | --- |
  | `pit-transactie-rente` | 118 | 0:51 | 1:06 | 1:59 |
  | `pit-rest` | 102 | 0:49 | 1:51 | 2:41 |

  Samen zijn de twee delen precies de 220 mutanten van een volledige run van bank-api: zonder overlap, met dezelfde status per mutant (218 gedood en dezelfde 2 levend, in `Cursor.lees` en `Transactie.isAfschrijving`). `pit-rest` duurt 34% langer, hoewel het minder mutanten heeft. In de eerste lokale run gaf één mutant `RUN_ERROR`, omdat een minion niet startte. In een tweede run was die mutant gewoon gedood.

- **Gemeten in CI** (run 43221071639, commit 96a7f5d):

  | Job | Duur | Mutanten |
  | --- | --- | --- |
  | bank-api (transactie en rente) | 4:04, waarvan bank-api 3:19 | 118, 116 gedood |
  | bank-api (de rest) | 6:44, waarvan bank-api 5:59 | 102, alle gedood |
  | bank-seed | 5:26 | 209, alle gedood (1 TIMED_OUT, de bekende in `Generator.aankopen`, ADR 0045) |
  | bank-domain en bank-bff | 1:21 | 41, alle gedood |
  | Langzaamste job van de acceptance stage (installatiescenario's 3/3) | 7:27, klaar na 10:24 | – |
  | **Pipeline** | **10:25**, tegen 11:41 en 16:05 in de twee runs ervoor | – |

  De langzaamste PIT-job was na 6:44 klaar, 3:36 eerder dan de acceptance stage. Daarmee is het doel uit Bewaking gehaald. In CI duurt `pit-rest` 80% langer dan `pit-transactie-rente`; lokaal was dat 34%. PIT bepaalt de duur van de pipeline nu niet, dus een andere verdeling levert op dit moment niets op.

## Bewaking
- **actionlint** in de commit stage (`scripts/iac-check.sh`) controleert de matrix in `ci.yml`.
- **De eerste push:** per PIT-job de duur, naast die van de langzaamste job van de acceptance stage. Het resultaat komt in deze ADR, onder Gevolgen. Is de langzaamste PIT-job niet eerder klaar dan de langzaamste job van de acceptance stage, dan gaat deze beslissing terug naar de gebruiker.
- **Daarna:** het aantal mutanten per deel in de samenvatting van PIT. Groeit `pit-rest` zo dat het duidelijk langer duurt dan `pit-transactie-rente`, dan volgt een nieuwe verdeling met een nieuwe ADR. Er is geen test die faalt als dat gebeurt.
