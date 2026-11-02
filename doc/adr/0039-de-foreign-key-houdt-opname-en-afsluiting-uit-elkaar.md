# 0039. De foreign key houdt een opname en de maandafsluiting uit elkaar

Datum: 2026-11-02
Status: Geaccepteerd

## Context
Een opname of inleg en de maandafsluiting kunnen tegelijk dezelfde spaarrekening raken, als de beheerder de datum vooruitzet. Beide hebben daarna de renterekening van BankSim nodig. Een opname of inleg vergrendelt eerst de spaarrekening en voor een rentecorrectie daarna de renterekening (ADR 0030). `LedgerService.boek` in de afsluiting vergrendelt ze in IBAN-volgorde (ADR 0026), dus meestal de renterekening eerst.

Het losse backlogpunt ging er daarom van uit dat dit tot een deadlock kon leiden. Dat klopt niet. De eerste stap van `MaandRente.boek` is het vastleggen van de maand in `rentebijschrijving`. Die tabel heeft een foreign key naar `rekening`, en die INSERT vraagt daardoor `FOR KEY SHARE` op de rij van de spaarrekening. Dat botst met de `FOR UPDATE` van een boeking:
- Heeft de opname de spaarrekening al, dan wacht de afsluiting bij het vastleggen, vóór ze een saldo leest of de renterekening vergrendelt.
- Heeft de afsluiting de maand al vastgelegd, dan wacht de opname op de spaarrekening. Na de commit ziet ze dat de maand is bijgeschreven en boekt ze een rentecorrectie.

Hetzelfde voorkomt een stillere fout: rente berekend over een saldo zonder de opname, terwijl geen rentecorrectie volgt.

`GelijktijdigeRenteTests` laat beide volgordes elke keer botsen. Zonder de foreign key gaf dat `deadlock detected`, rente van € 23,07 in plaats van € 21,92, en een ontbrekende rentecorrectie. Met de sleutel zijn de tests groen.

Opties:
1. **De foreign key als bescherming erkennen en bewaken.** Geen wijziging aan de productiecode. Nadeel: de bescherming is onzichtbaar in de Java-code, en hangt af van het lockgedrag van PostgreSQL.
2. **De spaarrekening expliciet vergrendelen in `MaandRente.boek`, vooraan.** Beter leesbaar en onafhankelijk van de sleutel. Maar er is geen falende test die erom vraagt, en het voegt een lock toe die er in de praktijk al is, ook voor elke maand die al is afgesloten.

## Beslissing
We kiezen optie 1. De foreign key van `rentebijschrijving` naar `rekening` hoort bij het ontwerp van de gelijktijdigheid, en de eerste stap van `MaandRente.boek` blijft het vastleggen van de maand. Het commentaar bij `MaandRente.boek` noemt dit.

## Gevolgen
- Er verandert geen productiegedrag. De IBAN-volgorde van ADR 0026 blijft gelden binnen `Grootboek.vergrendel`.
- Wie de sleutel weghaalt, of iets vóór het vastleggen van de maand zet, krijgt een rode test.
- De afsluiting van een spaarrekening wacht op een lopende boeking op die rekening, en omgekeerd. Andere rekeningen merken daar niets van.
- Haal je de foreign key om een andere reden weg, dan is optie 2 de vervanging, in een nieuwe ADR.

## Bewaking
- **`GelijktijdigeRenteTests`:** tegen PostgreSQL, in beide volgordes, zonder gegokte wachttijden. Hij wacht op `pg_stat_activity`. Rood gezien met sabotage: de foreign key tijdelijk weg, alle drie de tests rood.
