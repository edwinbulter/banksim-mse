# 0045. PIT geeft elke mutant 30 seconden extra voordat hij een time-out telt

Datum: 2026-11-08
Status: Geaccepteerd

## Context
De job "Commit stage – mutatietests" (ADR 0018) duurde in run 42418496233 17:14, bij een `timeout-minutes` van 20. Een iets tragere runner maakt de pipeline dan rood, zonder dat er een test faalt. Het meeste daarvan was `bank-api`: 10:47, waarvan 9:55 voor de mutatiefase.

**Wat PIT met een time-out doet.** PIT meet eerst hoe lang elke test duurt. Per mutant geeft hij die test daarna `timeoutFactor` × die tijd + `timeoutConstant`. Standaard is dat 1,25 × de tijd + 4000 ms. Duurt de test langer, dan stopt PIT de minion (de aparte JVM die de tests draait) en telt de mutant als TIMED_OUT. TIMED_OUT telt mee als gedood. De volgende mutant krijgt een verse minion.

**Wat er misgaat bij de tests met Testcontainers.** Bij het meten draaien de repositorytests in een minion die PostgreSQL en Spring al heeft opgestart. In een verse minion moet dat opnieuw: een container starten en de 14 Flyway-migraties draaien. Dat past niet in 1,25 × de gemeten tijd + 4 seconden. De mutant telt dan als TIMED_OUT, terwijl de test nog niet eens begonnen is. De volgende minion begint weer van voren.

**Meting op 2026-11-08,** lokaal, heel `bank-api`, 206 mutanten:

| | standaard (4000 ms) | `timeoutConstant` 30000 ms |
| --- | --- | --- |
| KILLED | 177 | 204 |
| TIMED_OUT | 27 | 0 |
| SURVIVED | 2 | 2 (dezelfde) |
| Mutatiefase | 3:16 | 2:27 |

- **Alle 27 TIMED_OUTs waren vals.** Met meer tijd doodt een echte test elk van die mutanten. In het log van de standaard run is te zien dat een minion werd gestopt terwijl hij nog Flyway draaide.
- **In CI** had het rapport van run 42418496233 42 TIMED_OUTs: 33 in `bank-api` en 9 in `bank-seed`. Alle 42 hebben `numberOfTestsRun 0` en geen `killingTest`. Ze zitten bijna allemaal in de klassen `Jdbc*`, die met Testcontainers getest worden.
- **De spreiding is groot.** Een eerdere standaard run duurde 210 s met 16 time-outs, even lang als de run met 30000 ms. De lokale winst van 49 seconden is één meting.

Opties:
1. **`timeoutConstant` op 30000 in `backend/pom.xml`.** Een verse minion heeft genoeg tijd voor PostgreSQL en Spring. Een mutant met een echte oneindige lus kost dan wel 30 seconden extra.
2. **Een lagere waarde, bijvoorbeeld 15000.** Kost minder bij een echte oneindige lus, maar is niet gemeten. Een tragere CI-runner kan er weer overheen gaan.
3. **Alleen `timeout-minutes` van de job verhogen.** De pipeline wordt niet rood, maar de valse TIMED_OUTs blijven. De score blijft dan mutanten tellen die geen test gedood heeft.
4. **Niets veranderen.**

## Beslissing
- `timeoutConstant` wordt 30000 (ms) in de PIT-configuratie van `backend/pom.xml`, voor alle modules.
- `timeoutFactor` blijft 1,25, en `timeout-minutes` van de job blijft 20.

## Gevolgen
- **Een TIMED_OUT betekent weer iets.** Bij de meting ging bank-api van 27 naar 0. Komt er toch een TIMED_OUT, dan heeft een mutant waarschijnlijk een echte oneindige lus gemaakt, of duurt het opstarten in CI langer dan 30 seconden.
- **De score blijft gelijk, maar klopt nu.** Met de standaard waarde was 99% inclusief 27 mutanten die geen test gedood had.
- **Een echte oneindige lus kost minstens 30 seconden per mutant.** In de huidige code heeft geen enkele mutant er zo een gemaakt (0 TIMED_OUT met 30000 ms).
- **Niet opgelost: het hangen.** Lokaal bleven op 2026-11-08 2 van de 6 runs van `bank-api` hangen. Volgens de thread dumps wachtte een minion zonder grens op de inlog bij PostgreSQL. Dat gebeurde terwijl de JUnit-plugin de tests zocht; daar geldt de time-out van PIT niet. Dat is een aparte beslissing.
- **Gemeten in CI** (run 42490223915, commit bce741a, vergeleken met run 42418496233):

  | | voor | na |
  | --- | --- | --- |
  | Job "Mutatietests (PIT)" | 17:14 | 14:12 |
  | `bank-api`, totaal / mutatiefase | 10:47 / 9:55 | 8:27 / 7:21 |
  | `bank-seed`, totaal / mutatiefase | 5:01 / 4:01 | 4:23 / 3:17 |
  | TIMED_OUT | 42 | 1 |

  De ene TIMED_OUT die over is, zit in `Generator.aankopen`. Daar wordt `i < aantal` omgedraaid tot `i >= aantal`. Bij nul geldopnames in een maand stopt die lus dan niet. Dat is een echte oneindige lus. De job heeft nu bijna 6 minuten over tot de grens van 20 minuten. Het meeste van die 14 minuten is nog steeds `bank-api`.

## Bewaking
- **Na de eerste push:** de duur van de job en het aantal TIMED_OUTs per module in het artifact `pit-rapport` (`mutations.xml`, status `TIMED_OUT`) vergelijken met run 42418496233 (17:14, 42 TIMED_OUTs). Het resultaat komt in deze ADR, onder Gevolgen.
- **Daarna:** het aantal TIMED_OUTs staat in de samenvatting van PIT in de joblog, per module. Het bekijken daarvan hoort bij het bekijken van de levende mutanten in de controle door de mens (werkwijze §10.1). Er is geen test die faalt als de waarde terugvalt naar de standaard.
