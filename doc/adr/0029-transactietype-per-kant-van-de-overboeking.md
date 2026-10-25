# 0029. Een overboeking heeft een transactietype per kant

Datum: 2026-10-25
Status: Geaccepteerd

## Context
Scenario "Inleggen" (S9.1, `e2e/specs/overschrijven.spec.ts`):
- Jan heeft € 1.000,00 op de betaalrekening en € 5.000,00 op de spaarrekening;
- hij legt € 250,00 in;
- op de spaarrekening staat een mutatie "Inleg", en op de betaalrekening een "Overschrijving" naar de eigen spaarrekening.

Rood gezien: via de API gaf `POST /api/overschrijvingen` 404. In chromium bestond de knop "Inleggen" niet.

Het FO zegt: "Na bevestigen ontstaan twee gekoppelde boekingen op de simulatiedatum: op de betaalrekening type 'Overschrijving', op de spaarrekening type 'Inleg' of 'Opname'."

**Het probleem:** in het grootboek (ADR 0024) heeft een overboeking nu één type (`Overboeking.type`). `JdbcGrootboek` schrijft dat type in de overboeking en in allebei de transacties. Bij een betaling en bij rente klopt dat: beide kanten hebben hetzelfde type. Bij een overschrijving verschillen ze.

Opties:
1. **Een type per kant.** `Overboeking` krijgt een type voor de kant van Van en een type voor de kant van Naar.
   - De bestaande constructor met één type geeft beide kanten hetzelfde type. Betalen en rente veranderen dus niet.
   - `overboeking.type` blijft het type van de overboeking als geheel (`OVERSCHRIJVING`).
   - `transactie.type` is het type van die kant. Er komt een migratie met `INLEG` en `OPNAME` in de CHECK.
   - Alles wat transacties leest (de lijst, de details, zoeken op type, de cursor) blijft ongewijzigd: het type staat al in de transactie.
2. **Eén type per overboeking, "Inleg" en "Opname" afgeleid bij het lezen.** In de database staat aan beide kanten `OVERSCHRIJVING`. Inleg of Opname volgt dan uit het soort rekening (spaar) en het teken van het bedrag.
   - Er komt geen schemawijziging.
   - Maar elk leespad moet het afleiden: de transactielijst, de details, zoeken op type en de frontend.
   - Zoeken op het type "Inleg" wordt een vertaling in de query.
   - De data zegt iets anders dan wat de klant ziet.
3. **Twee overboekingen**, één per kant, elk met een eigen type. Valt af: een overboeking heeft precies twee kanten met som 0 (ADR 0024, en de trigger in V6).

## Beslissing
We kiezen optie 1:
- `Overboeking` krijgt `typeVan` en `typeNaar`; `boekingen()` geeft elke kant zijn eigen type.
- De constructor met één type blijft bestaan voor betalen en rente.
- Migratie V12 voegt `INLEG` en `OPNAME` toe aan `transactie_type_check`.
- `TransactieType` krijgt `INLEG` en `OPNAME`.

## Gevolgen
- **Wat de klant ziet, staat zo in de data.** Zoeken op type, de details en de mutaties van de spaarrekening lezen het type zoals nu.
- **Een overboeking heeft voortaan drie typen:** het type van de overboeking en het type van elke kant. Bij betalen en rente zijn ze gelijk.
- **De databasetrigger van V6** controleert het aantal transacties, de twee rekeningen en de som, maar niet de types. Welk type bij welke kant hoort, bepaalt het domein (`Overboeking`). In de applicatie boekt alleen `LedgerService` (ArchUnit `AlleenLedgerServiceBoektTests`); alleen handmatige SQL in de database zou een verkeerde combinatie kunnen schrijven.
- **De rentecorrectie** (scenario 3 van S9.1) krijgt hier geen beslissing. Hoe `RenteService` op een overboeking reageert, is een eigen ADR (in de backlog: een domeinevent).

## Bewaking
- Unittest op `Overboeking`: een overschrijving boekt `OVERSCHRIJVING` bij Van en `INLEG` bij Naar, en de constructor met één type geeft beide kanten hetzelfde type.
- Repositorytest op `JdbcGrootboek`: elke transactie krijgt het type van haar eigen kant.
- Het scenario "Inleggen", via de API en in chromium.
