# 0030. De rentecorrectie volgt op een event van het grootboek, in dezelfde transactie

Datum: 2026-10-27
Status: Geaccepteerd

## Context
Scenario "Opname in het verleden corrigeert de latere rente" (S9.1, `e2e/specs/overschrijven.spec.ts`, `@simulatiedatum`):
- Jan heeft sinds 1 januari 2026 € 5.000,00 op de spaarrekening;
- de beheerder zet de simulatiedatum op 1 april 2026, en daarna terug op 15 februari 2026;
- Jan neemt € 1.000,00 op;
- de beheerder zet de simulatiedatum weer op 1 april 2026;
- op 28 februari staat een "Rentecorrectie februari" van −1,15, op 31 maart een "Rentecorrectie maart" van −2,55, en het spaarsaldo is € 4.033,38.

Rood gezien, in `simulatiedatum-rest` en `simulatiedatum-chromium`: de rente van januari (+12,74), februari (+11,54) en maart (+12,80) en de Opname stonden er, maar er was geen correctie.

De regel komt uit het TO (§7): na elke inleg of opname rekent de bank de rente van die maand en alle latere maanden opnieuw uit. Het verschil boekt hij via `LedgerService` als correctie, met type `RENTE`. Bestaande boekingen blijven staan.

**De vraag is hoe de rente-code (`nl.banksim.api.rente`) hoort dat er geboekt is.** Het grootboek (`nl.banksim.api.grootboek`) boekt de overschrijving. De rente-code hangt al van het grootboek af: `MaandRente` boekt via `LedgerService` (ADR 0028). Beide zitten in `bank-api`, in dezelfde JVM.

Opties:
1. **`OverschrijfService` roept na het boeken de rente-code aan.** Dat is het eenvoudigst. Maar elke andere plek die op een spaarrekening boekt, moet eraan denken, en niets bewaakt dat.
2. **Het grootboek publiceert een Spring-event `OverboekingGeboekt`; de rente-code luistert met `@EventListener`.** De listener draait synchroon, in dezelfde thread en dezelfde databasetransactie. Het grootboek kent de rente-code niet. Dit kan met Spring zelf, en het origineel doet het ook zo.
3. **Hetzelfde event via Spring Modulith** (`@ApplicationModuleListener`). Dat is een nieuwe library. Zo'n listener draait standaard pas na de commit, asynchroon. Dan kunnen de opname en haar correcties los van elkaar slagen of mislukken, en kan "nooit rood" niet meer ná de correcties gecontroleerd worden (TO §6).

## Beslissing
We kiezen optie 2:
- **Het event:** `OverboekingGeboekt(Overboeking)` in `nl.banksim.api.grootboek`. `LedgerService.boek` publiceert het na het schrijven, met de `ApplicationEventPublisher` van Spring.
- **De listener:** `RenteService` luistert met `@EventListener`, synchroon, in de transactie van de boeking. Hij negeert overboekingen van het type `RENTE`; dat zijn de bijschrijvingen en de correcties zelf.
- **De herberekening:** voor elke kant die een spaarrekening is, en voor elke maand vanaf de maand van de boeking waarvan de rente al is bijgeschreven (tabel `rentebijschrijving`, ADR 0028):
  - de rente opnieuw uitrekenen uit de dagsaldi, zonder de rente en correcties van die maand zelf;
  - het verschil met wat er al voor die maand geboekt is, als correctie boeken via `LedgerService`, op de laatste dag van de maand om 23:59, met de omschrijving "Rentecorrectie februari";
  - maand voor maand, zodat een correctie meetelt in de dagsaldi van de maanden erna.

  Een maand die nog niet is afgesloten, krijgt bij de afsluiting meteen de goede rente.

Spring Modulith en de fitness function `verify()` uit de ADR-backlog zijn een andere beslissing; die krijgen een eigen ADR.

## Gevolgen
- De opname en haar correcties worden samen gecommit, of samen teruggedraaid.
- Elke boeking via `LedgerService` op een spaarrekening leidt tot een herberekening, ook een toekomstige soort boeking. Daar hoeft niemand aan te denken.
- **Het grootboek hangt niet van de rente-code af**, terwijl de rente-code wel van het grootboek afhangt. Een kring in de afhankelijkheden is er dus niet.
- **Een event binnen het proces is onzichtbaarder dan een aanroep**: wie `LedgerService.boek` leest, ziet niet dat er daarna rente wordt geboekt. De Javadoc van `LedgerService` en van `OverboekingGeboekt` noemt het.
- **Nooit rood ná de correcties (TO §6)** wordt nog niet afgedwongen: `LedgerService` controleert vóór het publiceren. Minder rente kan een latere opname onder nul brengen. Dat krijgt een eigen scenario.
- **Komen het grootboek en de rente-code ooit in aparte services**, dan werkt dit zo niet meer: dan is een berichtenstroom nodig, met correcties die later binnenkomen. Dat vraagt een nieuwe ADR.

## Bewaking
- **ArchUnit:** klassen in `..grootboek..` hangen niet af van `..rente..`.
- **Unittests op `RenteService`:**
  - een opname in een afgesloten maand boekt een correctie voor die maand en de maanden erna;
  - een overboeking van het type `RENTE` leidt niet tot een herberekening;
  - een maand zonder bijschrijving krijgt geen correctie;
  - zonder verschil wordt er niets geboekt.
- **Unittest op `LedgerService`:** na het schrijven publiceert hij `OverboekingGeboekt`; bij een weigering niet.
- **Het scenario** "Opname in het verleden corrigeert de latere rente", in `simulatiedatum-rest` en `simulatiedatum-chromium`.
