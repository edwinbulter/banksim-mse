# 0013. `SimulationClock` vervangt de systeemtijd

Datum: 2026-10-22
Status: Geaccepteerd

## Context
Slice 3, S3.1 (`e2e/specs/simulatiedatum.spec.ts`). De beheerder zet de bank op een gekozen datum. Met simulatiedatum 5 oktober 2026 ziet Jan een transactie van 10 oktober niet, en telt die niet mee in het saldo. Zonder simulatiedatum draait de bank op vandaag (TO §8).

Nu kijkt geen enkele query naar een datum: alles wat geboekt is, telt mee.

Opties:
1. **Een `SimulationClock` in `bank-api`** die "vandaag voor de bank" geeft: de simulatiedatum uit de database, of de echte datum in Europe/Amsterdam als er geen is. De queries krijgen die datum als parameter.
2. **De datum in elke query uit de tabel lezen** (`WHERE datum <= (SELECT … FROM instelling)`). Er komt geen klasse bij, maar de regel "zonder simulatiedatum is het vandaag" staat dan in SQL, en die is lastig los te testen.
3. **Een `Clock`-bean die Spring vervangt** door een vaste klok. Die geldt dan voor de hele JVM en kan niet per verzoek veranderen zonder herstart.

## Beslissing
- **Klok.** `bank-api` krijgt een `SimulationClock`. Die heeft één methode, `vandaag()`: de simulatiedatum als die gezet is, anders `LocalDate.now(clock)` met een `java.time.Clock` in de zone Europe/Amsterdam. In tests is die `Clock` vast.
- **Opslag.** De simulatiedatum staat in een nieuwe tabel `instelling` (migratie `V3`), met hooguit één rij. Wissen betekent: geen rij, en dus vandaag.
- **Queries.** De saldo-query en de transactie-query krijgen `datum <= :vandaag`.
- **API.** `PUT /api/beheer/simulatiedatum` met `{"datum": "2026-10-05"}` zet de datum, en `DELETE` wist hem. Alleen de rol `beheerder` mag dat (ADR 0015).

## Gevolgen
- Elke query die over "nu" gaat, moet de datum van de `SimulationClock` gebruiken. Nieuwe slices (betalen, rente) erven die regel.
- De zone staat expliciet vast. De pod draait in UTC, en anders zou tussen middernacht en 2 uur de verkeerde dag gelden.
- Elk verzoek leest één rij extra. Dat is verwaarloosbaar; een cache volgt pas als metingen erom vragen.
- Repositorytests krijgen een vaste datum, zodat ze niet afhangen van de dag waarop ze draaien.

## Bewaking
- Het scenario "Transacties na de simulatiedatum zijn onzichtbaar", in alle drivers: saldo én transactielijst.
- Unittests van `SimulationClock`: met en zonder simulatiedatum, en de zone rond middernacht.
- Repositorytests (Testcontainers): een transactie na de datum telt niet mee, en staat niet in de lijst.
- ArchUnit in `bank-api`: geen `LocalDate.now()`, `LocalDateTime.now()` of `Instant.now()` zonder `Clock`-argument.
