# 0020. De seed draait als één sessie per Playwright-worker

Datum: 2026-10-24
Status: Geaccepteerd

## Context
ADR 0003 start `bank-seed` per aanroep als losse pod (`kubectl run --rm --attach`). Daar staat: "Een pod starten kost een paar seconden per scenario. Dat is acceptabel voor de huidige omvang." Die omvang is nu bereikt.

**In CI** (run 40030250651):
- de Gegeven- en En-stappen kostten samen ~1100 s van de 1250 s testtijd;
- een klant aanmaken kostte gemiddeld 8,8 s, een boeking 5,0 s;
- het scenario "Transacties per datum" liep met 4 workers twee keer in de time-out van 30 s (run 40086956911).

**Lokaal**, gemeten in de spike van 2026-10-24:

| Meting | Tijd |
| --- | --- |
| Seed met echt werk (een beheerder aanmaken) | 3,8–4,0 s |
| Seed zonder werk (onbekend commando, alleen opstarten) | 4,0 s |
| Een kale pod | 2,0 s |

Het werk zelf (Keycloak en de database) kost dus vrijwel niets. De tijd zit in de pod (~2 s) en in het opstarten van de JVM met Spring (~2 s), bij elke aanroep opnieuw.

Opties:
1. **Pod per aanroep** (nu). Eenvoudig, maar elke Gegeven-stap kost 4–9 s.
2. **Een sessie per worker.** Per Playwright-worker één seed-pod die opdrachten van stdin leest en per opdracht één antwoordregel schrijft. Alleen de eerste aanroep betaalt de opstart.
3. **Een permanente seed-Deployment in de chart**, die de DSL aanroept. Afgewezen: testgereedschap hoort niet in de installatie, en het wordt een extra aanvalsvlak (zoals optie 3 van ADR 0003).
4. **De Gegeven-stappen van een scenario bundelen** in één aanroep. Afgewezen: de stappen zouden dan niet meer uitvoeren op het moment dat ze gerapporteerd worden, en het scheelt alleen bij scenario's met meerdere stappen.

## Beslissing
We kiezen optie 2. Dit vervangt in ADR 0003 alleen het onderdeel "losse pod per aanroep". De seed-CLI, het Secret `bank-seed`, de rechten en de JSON-uitvoer blijven zoals ze zijn.

- **Commando `sessie`.** `bank-seed sessie` leest per regel een JSON-array met de argumenten van één opdracht, bijvoorbeeld `["klant","--alias","jan-…","--betaalrekening","1842.17"]`. Elke opdracht gaat door dezelfde `SeedCommando` als nu.
- **Antwoord.** Per opdracht schrijft de sessie precies één regel met een vaste prefix: `BANKSIM-SEED ` plus de JSON van nu, of `{"fout":"…"}`. Andere uitvoer, zoals logregels, negeert de driver. Een fout stopt de sessie niet.
- **Einde.** Bij het einde van stdin stopt de sessie. De pod draait met `--rm`, zodat hij daarna verdwijnt, ook als de worker crasht en stdin daardoor sluit.
- **De shell-driver** start de sessie lui, bij de eerste aanroep, één per worker, met `kubectl run -i --rm --restart=Never`. Hij stuurt de opdrachten één voor één en sluit stdin bij het einde van de worker. De image-tag leest hij één keer.
- **Het project `infra`** houdt pod per aanroep. Daar vervangt een upgrade de installatie midden in de run, en een sessie zou dan nog de oude versie draaien.

## Gevolgen
- Een Gegeven-stap kost naar verwachting minder dan een seconde, behalve de eerste per worker. De acceptatietests worden daardoor naar schatting enkele minuten korter, en de time-out van 30 s raakt niet meer op door de seed.
- Er draait per worker een pod zolang de testrun loopt: lokaal tot 5, in CI 2.
- Blijft een sessie hangen, dan faalt de stap op de time-out van het scenario. De melding moet dan zeggen welke opdracht openstond.
- Een opdracht die faalt, geeft dezelfde leesbare melding als nu. De reden van een pod die niet start (bijvoorbeeld `ErrImagePull`) blijft gelden voor de start van de sessie.
- Daarna kunnen we opnieuw meer workers in CI proberen. Dat is uitgesteld bij run 40086956911.

## Bewaking
- Unittests van `bank-seed`:
  - de sessie verwerkt meerdere opdrachten na elkaar;
  - een foute opdracht geeft `{"fout":…}` en de volgende werkt nog;
  - aan het einde van stdin stopt de sessie.
- De acceptatiescenario's gebruiken de sessie bij elke Gegeven-stap.
- Na `make e2e` staan er geen `bank-seed-*`-pods meer in `banksim-mse`. Dat controleer ik bij de bouw met de hand; daarna is het zichtbaar in `kubectl get pods`.
- De installatiescenario's (project `infra`) bewaken het pad met een pod per aanroep.
