# 0046. Elke testdatabase komt uit de helper van zijn module, met een grens op de inlog

Datum: 2026-11-08
Status: Geaccepteerd

## Context
Op 2026-11-08 bleven lokaal 2 van de 6 runs van PIT op `bank-api` hangen, op 0% CPU. Gisteravond bleef één run elf uur staan. De thread dumps lieten zien waar:
- **De minion** stond in de `@BeforeAll` van `JdbcRekeningRepositoryTests`. Flyway opende daar de eerste verbinding, en de driver wachtte in `ConnectionFactoryImpl.doAuthentication` op het antwoord van PostgreSQL. De PostgreSQL-container draaide, maar het antwoord kwam niet. De driver van PostgreSQL wacht standaard zonder grens (`loginTimeout` 0).
- **PIT** wachtte op de minion, ook zonder grens. Het gebeurde terwijl de JUnit-plugin de tests zocht en daarbij de `@BeforeAll` al uitvoerde. Daar geldt de time-out van PIT niet (ADR 0045 helpt dus niet).

Waarom PostgreSQL de inlog niet beantwoordde, is niet gemeten. In CI is het hangen nog niet gezien. Gebeurt het daar, dan breekt de job pas af na `timeout-minutes` (20).

**Na te bootsen met `docker pause`.** Een stilgezette container neemt de verbinding nog aan, maar antwoordt niet. Een test die zo een verbinding opent, bleef staan op precies dezelfde regel: `doAuthentication`, regel 804.

**Waar de database van een test vandaan komt:**
- 16 repositorytests (8 in `bank-api`, 8 in `bank-seed`) maakten elk hun eigen `PostgreSQLContainer`. In `bank-api` stond in elk bestand de image met digest.
- De tests van `bank-api` die de hele applicatie starten, krijgen hun container uit `PostgresTestDatabase`, via `@ServiceConnection`.

Opties:
1. **`loginTimeout` in de JDBC-URL, op één plek per module, met een bronscan die dat bewaakt.** `withUrlParam` zet hem in `getJdbcUrl()`. Die gebruiken zowel de repositorytests als `@ServiceConnection`: in Spring Boot 4.0.8 geeft `JdbcContainerConnectionDetails.getJdbcUrl()` die van de container door.
2. **Hetzelfde, maar ook `socketTimeout`.** Die begrenst ook een query die halverwege blijft hangen. Zo'n hang is niet gezien (YAGNI). Een te krappe waarde kan een test die bewust op een lock wacht, laten falen.
3. **De grens in elke test apart.** Zonder bronscan vergeet de volgende test hem.
4. **Een time-out in PIT of in Maven.** PIT heeft geen grens voor het zoeken van de tests. Een grens om de hele job maakt de pipeline rood, maar wijst niet aan waar het hangt.

## Beslissing
- Elke `PostgreSQLContainer` in de tests komt uit de helper van zijn module: `PostgresTestDatabase.container()` in `bank-api`, `TestDatabase.container()` in `bank-seed`.
- De helper zet `loginTimeout=10` (seconden) in de JDBC-URL.
- Geen `socketTimeout`.

## Gevolgen
- **Een PostgreSQL die niet antwoordt, geeft na 10 seconden een fout:** "Connection attempt timed out". Daarmee faalt die test, in plaats van te blijven wachten. Hoe PIT zo'n fout afhandelt terwijl hij de tests zoekt of de dekking meet, is niet getest; de minion wacht in elk geval niet meer zonder grens.
- **Dit verklaart niet waarom PostgreSQL soms niet antwoordt.** Dat blijft open. Komt het vaak voor, dan zie je het nu als een rode test met die melding.
- **Een verwante fout blijft:** de verbinding wordt meteen gesloten. Bij de invoering faalde `JdbcSimulatiedatumRepositoryTests` één keer met "The connection attempt failed", door een `EOFException` in `ConnectionFactoryImpl.enableSSL`. Dat is geen hang, en `loginTimeout` verandert er niets aan. Daarna gingen 10 runs van de databasetests van `bank-api` (90 containers) en de hele commit stage goed. Lokaal draait Docker op OrbStack; of de port forwarding daarvan de oorzaak is, is niet onderzocht.
- **De image met digest staat nog maar op twee plekken in de tests:** in elke helper één keer. `values.yaml` blijft de bron (ADR 0009).
- **Een query die halverwege blijft hangen, wacht nog altijd zonder grens.** Dat is de afweging van optie 2.

## Bewaking
- `PostgresTestDatabaseTests` (`bank-api`) en `TestDatabaseTests` (`bank-seed`) zetten de container stil met `docker pause` en verwachten binnen 30 seconden een fout over de time-out. Zonder `loginTimeout` bleven beide hangen tot de grens van JUnit; dat is rood gezien.
- `TestdatabaseUitDeHelperTests` (`bank-api`, architectuur) leest de testbronnen van alle modules. Hij faalt bij `new PostgreSQLContainer` buiten de twee helpers. Rood gezien met een sabotage: een losse container in `bank-seed/.../JdbcContactenTests`.
