# 0042. De generator boekt een inleg als overboeking met twee kanten

Datum: 2026-11-03
Status: Geaccepteerd

## Context
Scenario "Elke klant legt elke maand een vast bedrag in op zijn spaarrekening" (S11.1, `e2e/specs/infra/huishoudens.spec.ts`).

Rood gezien, in project `infra`: `maanden zonder precies één inleg bij jdevries`, met alle 63 maanden in de lijst.

Tot nu toe schrijft de generator alleen eenzijdige boekingen, omdat de tegenpartij geen rekening bij BankSim heeft (ADR 0024, 0041). Bij een inleg is dat anders: beide rekeningen staan bij BankSim. ADR 0024 zegt dat zo'n beweging een overboeking met twee kanten is. Dezelfde ADR zegt ook dat de seed `overboeking` niet raakt, en dat `LedgerService` de enige code in `bank-api` is die overboekingen schrijft.

Opties:
1. **Rechtstreeks in de database, als overboeking met twee kanten.** `bank-seed` maakt de `Overboeking` uit `bank-domain` (`Overboeking.inleg`) en schrijft één rij `overboeking` en twee rijen `transactie`, als gebruiker `bank_seed`, die al INSERT heeft op die tabellen. De databasetrigger controleert de overboeking bij de commit.
2. **Twee losse eenzijdige rijen.** Het minste werk, en de trigger ziet ze niet. Maar de data wijkt dan af van een inleg die de klant zelf doet, en de controle uit het TO dat alle overboekingen samen op nul uitkomen, slaat ze over.
3. **Via de API van `bank-api`, zodat `LedgerService` boekt.** Dat is de echte route, maar ADR 0041 wees dit al af: 63 inleggen per klant met inloggen maken de installatie veel trager, en de boekingen liggen deels in de toekomst.

## Beslissing
We kiezen optie 1:
- **`Overboekingen`**, een nieuwe opslaginterface in `bank-seed`, met `JdbcOverboekingen`. De SQL staat daar, zoals `AlleenOpslagKentSqlTests` eist.
- **`bank-seed` hangt niet van `bank-api` af** (ADR 0003). De SQL lijkt dus op die van `JdbcGrootboek`; die herhaling accepteren we.
- **Alleen voor de generator.** De andere seed-opdrachten blijven eenzijdig schrijven. Dit vult ADR 0024 aan voor `bank-seed demo`; de rest van ADR 0024 blijft gelden.

Deze ADR gaat alleen over hoe een inleg wordt geschreven. De opname bij een dreigend tekort en de rente krijgen een eigen scenario, en waar nodig een eigen ADR.

## Gevolgen
- Een inleg uit de generator ziet er hetzelfde uit als een inleg van de klant: dezelfde typen per kant (ADR 0029), en de betaalrekening en spaarrekening als elkaars tegenpartij.
- De som-invariant geldt ook voor de inleggen van de demo-data.
- **`LedgerService` draait hier niet.** Er komt dus geen `OverboekingGeboekt`, en geen rentecorrectie. Dat is nu geen probleem, omdat de spaarrekeningen van de demo-klanten geen datum van openen hebben en de maandafsluiting ze overslaat (ADR 0040). Bij de stap over rente moet dit opnieuw worden bekeken.
- **`NooitRoodRegel` past de generator zelf toe**, zoals bij de andere boekingen. De inleg telt mee als vaste boeking: de boodschappen houden er rekening mee, en een inleg die de betaalrekening rood zou maken, laat de K8s Job falen.
- De spaarrekening krijgt nog geen beginsaldo en geen rente. Het spaarsaldo is voorlopig de som van de inleggen.

## Bewaking
- **Scenario:** "Elke klant legt elke maand een vast bedrag in op zijn spaarrekening".
- **`JdbcOverboekingenTests`** (Testcontainers): één overboeking met twee transacties, elk met het juiste type en de andere rekening als tegenpartij; de trigger accepteert het bij de commit.
- **`DemoDataDatabaseTests`:** een mislukte login laat ook geen overboekingen achter.
- **`AlleenOpslagKentSqlTests`:** de SQL staat in `JdbcOverboekingen`.
- **De databasetrigger** uit `V6__grootboek.sql`: een overboeking die niet in balans is, faalt bij de commit.
