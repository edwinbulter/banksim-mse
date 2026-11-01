# 0036. Nooit rood geldt ook voor rentecorrecties

Datum: 2026-11-01
Status: Geaccepteerd; vult de Gevolgen van [0030](0030-rentecorrectie-via-een-event-van-het-grootboek.md) aan

## Context
ADR 0030 zegt onder Gevolgen: "Nooit rood ná de correcties (TO §6) wordt nog niet afgedwongen: `LedgerService` controleert vóór het publiceren. Minder rente kan een latere opname onder nul brengen." Daarvoor stond een los backlogpunt in de werkwijze, met een eigen scenario.

Bij het uitwerken bleek die zin niet te kloppen. Een negatieve rentecorrectie is zelf een overboeking van de spaarrekening naar BankSim, en gaat via `LedgerService.boek`. Daar geldt "nooit rood" (ADR 0025) voor elke rekening behalve een interne. Zou een correctie de spaarrekening rood maken, dan gooit `LedgerService` een `SaldoOntoereikend`; de correctie zit in dezelfde transactie als de opname (ADR 0030), dus de hele opname wordt teruggedraaid.

Een proef tegen de installatie bevestigde het: met de rente t/m maart, € 1.000,00 over op 1 april, en daarna een opname van € 1.000,00 op 15 februari (zonder correcties precies € 0,00, met de correcties −3,70) weigerde de bank de opname met "saldo ontoereikend", en boekte ze niets.

Opties:
1. **Zo laten.** Een correctie valt onder dezelfde regel als elke andere boeking. Geen extra code.
2. **Correcties uitzonderen van "nooit rood"**, zodat een opname in het verleden altijd lukt en de spaarrekening door de correcties rood mag worden. Dat vraagt een uitzondering in `LedgerService`, dus meer code.

## Beslissing
We kiezen optie 1. Er verandert niets aan de code. Een unittest legt het gedrag vast.

## Gevolgen
- De zin in ADR 0030 over "nog niet afgedwongen" geldt niet; deze ADR vult hem aan. Het losse backlogpunt is opgelost.
- Een opname in het verleden kan worden geweigerd door de correcties die ze zou veroorzaken, ook als het saldo zonder correcties genoeg is. De melding is dezelfde: "saldo ontoereikend".
- Wie later een uitzondering voor rente in `LedgerService` maakt, ziet de test hieronder rood worden.

## Bewaking
- `RenteServiceTests.eenOpnameWaarvanDeCorrectiesDeSpaarrekeningRoodZoudenMakenWordtGeweigerd`, tegen PostgreSQL: de opname geeft `SaldoOntoereikend`, en er staan geen opname en geen correcties op de spaarrekening. Sabotage: rentecorrecties uitzonderen van "nooit rood" in `LedgerService` maakte hem rood.
