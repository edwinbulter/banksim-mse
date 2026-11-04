# 0043. De generator boekt de rente van de demo-klanten

Datum: 2026-11-04
Status: Geaccepteerd

## Context
Scenario "Elke spaarrekening krijgt elke maand rente, van oktober 2021 t/m december 2026" (S11.1, `e2e/specs/infra/huishoudens.spec.ts`).

Rood gezien, in project `infra`: `rente van Jan de Vries op 2021-10-31` gaf een lege lijst in plaats van `16.79`.

De spaarrekeningen van de demo-klanten hebben geen datum van openen (ADR 0040), dus de maandafsluiting slaat ze over. De vraag is wie de rente boekt.

Opties:
1. **De maandafsluiting van `bank-api`.** De rekeningen krijgen een datum van openen, en de afsluiting uit ADR 0028 doet de rest. Er is geen nieuwe code voor het boeken nodig. Maar de rente verschijnt pas om 00:05 of als de beheerder de simulatiedatum zet, terwijl S11.1 demonstreren zonder voorbereiding vraagt. De eindsaldi horen dan niet bij de generator, terwijl de golden master (stap 5 van S11.1) ze wil vastleggen. En één aanroep die de simulatiedatum zet, boekt in één keer ongeveer 630 overboekingen.
2. **De generator in `bank-seed`.** De generator berekent de rente met `RenteCalculator` uit `bank-domain` en schrijft haar samen met de andere boekingen van de klant, zoals ADR 0041 voor de historie koos. ADR 0028 wees vooraf boeken alleen af voor rekeningen die in een scenario ontstaan.

## Beslissing
We kiezen optie 2:
- **`Generator.rente`** rekent per maand met `RenteCalculator`, over het eindsaldo per dag van de spaarrekening: beginsaldo, inleggen, opnames en de rente van de maanden ervoor.
- **De rente is een overboeking** van "BankSim" (renterekening `NL24SIMB0000000000`, V11) naar de spaarrekening, met type `RENTE`, op de laatste dag van de maand om 23:59, als "Rente oktober". Dat is dezelfde vorm als in `MaandRente`.
- **`Rentebijschrijvingen`**, een nieuwe opslaginterface met `JdbcRentebijschrijvingen`, legt per maand een rij vast in `rentebijschrijving`. Daardoor slaat de maandafsluiting deze maanden over.
- **De spaarrekeningen krijgen 1 oktober 2021 als datum van openen.** Zo gelden de maandafsluiting, de lopende rente en de rentecorrectie uit ADR 0030 ook voor de demo-klanten. Dit vervangt de zin in ADR 0040 dat ze voorlopig geen datum van openen krijgen.
- **`bank_seed` heeft geen nieuwe rechten nodig:** de rol heeft al `SELECT` en `INSERT` op alle tabellen.

## Gevolgen
- De rente staat er direct na de installatie, ook voor de maanden na vandaag; die zijn onzichtbaar tot de simulatiedatum daar komt (ADR 0013).
- Een maand zonder rente krijgt geen rij. De afsluiting legt die maand later zelf vast, en boekt ook niets.
- **Twee plekken kennen de vorm van een rentebijschrijving:** `MaandRente` en de generator. Die herhaling accepteren we, omdat `bank-seed` niet van `bank-api` afhangt (ADR 0003). Het scenario merkt het als de afsluiting een maand niet als afgehandeld herkent: dan staan er twee bijschrijvingen.
- `LedgerService` draait hier niet, net als bij ADR 0042. Een latere inleg of opname van de klant loopt wel via `LedgerService`, die de rente van de al geboekte maanden corrigeert. Dat is de bestaande route voor een rekening met geboekte rente (ADR 0030); een scenario voor de correctie op demo-data is er niet.
- Een bestaande installatie krijgt dit niet (ADR 0041): gebruik eerst `make uninstall`.

## Bewaking
- **Scenario:** "Elke spaarrekening krijgt elke maand rente, van oktober 2021 t/m december 2026": de rente van Jan, precies één bijschrijving per maand ook nadat de afsluiting heeft gedraaid, en het eindsaldo.
- **`GeneratorTests`:** de bedragen, de eindsaldi, en geen rente zonder spaargeld.
- **`DemoDataDatabaseTests`:** direct na het schrijven staan de rente en de rijen in `rentebijschrijving` in de database, zonder `bank-api`; een mislukte login laat er niets van achter.
- **`JdbcRentebijschrijvingenTests`** (Testcontainers): de rij in `rentebijschrijving`.
- **`AlleenOpslagKentSqlTests`:** de SQL staat in `JdbcRentebijschrijvingen`.
- **De databasetrigger** uit `V6__grootboek.sql`: de overboeking is in balans.
