# 0028. Rente wordt bijgeschreven door een maandafsluiting, als boeking in het grootboek

Datum: 2026-10-25
Status: Geaccepteerd

## Context
Scenario "Rente wordt maandelijks bijgeschreven" (S8.1, `e2e/specs/spaarrekening.spec.ts`, `@simulatiedatum`):
- Jan opent op 1 januari 2026 een spaarrekening met € 10.000,00;
- de beheerder zet de simulatiedatum op 1 februari 2026;
- op 31 januari staat een mutatie "Rente" van +25,48.

Rood gezien, in `simulatiedatum-rest`: de spaarrekening had geen mutaties. In `simulatiedatum-chromium`: het overzicht had geen knop voor de spaarrekening.

De regels (FO, TO):
- 3% per jaar, dagelijks berekend over het eindsaldo van de dag, met 365 of 366 dagen;
- tussenresultaten op 10 decimalen, afgerond bij het bijschrijven;
- bijgeschreven als mutatie van het type Rente, op de laatste dag van de maand.

**De vraag is wanneer en hoe die mutatie ontstaat.** De simulatiedatum kan vooruit en terug (ADR 0013), en een betaling mag geen latere boeking rood laten staan (ADR 0025).

Opties:
1. **Niet boeken, maar berekenen bij het lezen.** De rente-mutaties zijn afgeleid: voor elke afgesloten maand tot "vandaag" berekend. Daar is geen taak voor nodig, en de simulatiedatum werkt vanzelf in beide richtingen. Maar de rente staat dan niet in het grootboek. Elke saldoberekening moet haar optellen: het overzicht, de lijst, nooit rood (ADR 0025). En het geld komt nergens vandaan, terwijl ADR 0024 voor elke beweging twee kanten eist.
2. **Een maandafsluiting die boekt via `LedgerService`.** De rente is een overboeking van een interne renterekening van BankSim naar de spaarrekening, op de laatste dag van de maand. De afsluiting draait:
   - als de beheerder de simulatiedatum zet of wist, voor alle maanden die sindsdien zijn afgesloten;
   - één keer per dag met een geplande taak, voor de echte datum.

   Per rekening en maand gebeurt dat hooguit één keer, bewaakt door een unieke sleutel. Zo werkt een echte bank ook: met een batch aan het eind van de maand.
3. **Lui boeken bij het lezen.** Wie een spaarrekening of het overzicht opent, laat eerst de ontbrekende maanden boeken. Er is dan geen taak nodig, maar een GET schrijft in de database, en elk leespad moet eraan denken (ook nooit rood).
4. **Vooraf boeken tot het eind van de data**, zoals de generator van het origineel. Daarna volgt bij elke inleg of opname een correctieboeking. Dat werkt voor gegenereerde data, maar een rekening die in een scenario ontstaat, krijgt zo niets.

## Beslissing
We kiezen optie 2:
- **Domein** (`bank-domain`): `RenteCalculator` berekent de rente van één maand uit het eindsaldo per dag: 3% / 365 of 366, scale 10, en afronden (HALF_UP) bij het bijschrijven. Hij rekent pas vanaf de dag waarop de rekening geopend is (`geopend_op`, V10).
- **De interne renterekening "BankSim Rente"** is een rekening van BankSim zonder login, met de soort `intern`. Nooit rood (ADR 0025) geldt alleen voor betaal- en spaarrekeningen: de bank betaalt de rente.
- **`RenteService.sluitMaandenAf(tot)`** boekt voor elke spaarrekening en elke afgesloten maand tot en met `tot` die nog geen rente heeft, via `LedgerService`, met type `RENTE` en omschrijving "Rente januari". Een tabel `rentebijschrijving` (rekening, maand) maakt de afsluiting idempotent.
- **Wanneer:**
  - `SimulatiedatumController` roept de afsluiting aan na het zetten en na het wissen;
  - een `@Scheduled`-taak doet het dagelijks om 00:05 in Europe/Amsterdam, tot en met "vandaag" voor de bank. Om 00:05 is de maand van gisteren voorbij.

## Gevolgen
- Rente staat in het grootboek, met twee kanten. De som-invariant geldt ook voor rente; de renterekening daalt evenveel als de spaarrekeningen stijgen.
- **De simulatiedatum terugzetten** wist geen rente. Bijgeschreven rente na de simulatiedatum is onzichtbaar, zoals elke latere transactie (ADR 0013).
- **Inleg en opname midden in een al afgesloten maand** komen pas met de overschrijvingen (slice 9). Dan is een correctieboeking nodig (TO §6). Dat krijgt dan een eigen scenario en eventueel een ADR.
- **Volgorde in scenario's:** "Gegeven" maakt eerst de rekening, en daarna zet de beheerder de datum. Andersom draait de afsluiting pas bij de volgende wijziging of de nachtelijke taak.
- **Twee replica's** (later) kunnen de taak allebei draaien. De unieke sleutel voorkomt een dubbele boeking.
- **Wissen na een scenario:** de fixture wist de simulatiedatum na elk `@simulatiedatum`-scenario. Ook dat start een afsluiting, tot vandaag. Testrekeningen krijgen dus rente voor alle maanden sinds hun openingsdatum; dat is onschadelijk.

## Bewaking
- **jqwik op `RenteCalculator`:** de rente is nooit negatief, en stijgt (monotoon) met het saldo (werkwijze §7).
- **Unittests op `RenteCalculator`:**
  - januari 2026 met € 10.000,00 geeft € 25,48;
  - een schrikkeljaar rekent met 366 dagen;
  - de rente telt pas vanaf `geopend_op`;
  - een saldo dat halverwege de maand verandert.
- **Repositorytest:** de afsluiting is idempotent; twee keer draaien boekt één keer.
- **Controllertest:** het zetten en het wissen van de simulatiedatum roepen de afsluiting aan.
- **De scenario's** van S8.1.
- **PIT op `bank-domain`:** draait al in de commit stage (ADR 0018).
