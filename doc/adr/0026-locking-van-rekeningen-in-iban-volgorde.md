# 0026. Gelijktijdige boekingen: de rekeningen vergrendelen in IBAN-volgorde

Datum: 2026-10-25
Status: Geaccepteerd

## Context
`LedgerService.boek` leest het saldoverloop van de betalende rekening, controleert nooit rood (ADR 0025) en schrijft de overboeking (ADR 0024), allemaal in één databasetransactie. Onder READ COMMITTED, de standaard van PostgreSQL, lezen twee gelijktijdige transacties hetzelfde saldo. Ze keuren dan allebei hun betaling goed, en samen laten ze de rekening rood staan.

Fitness function `GelijktijdigeOverboekingenTests` (werkwijze §7):
- 10 rekeningen van € 100,00;
- 100 gelijktijdige overboekingen van € 30,00 kriskras, met 20 threads.

Rood gezien, 3 van de 3 keer: één rekening eindigde op -€ 20,00, -€ 50,00 en -€ 20,00. Het totaal bleef € 1.000,00, dus de double-entry hield wel stand.

Kriskras betekent: A betaalt aan B terwijl B aan A betaalt. Wie vergrendelt, moet een deadlock voorkomen.

Opties:
1. **Pessimistisch:** `SELECT … FOR UPDATE` op beide rekeningen, altijd in IBAN-volgorde, als eerste stap van `boek`. Een tweede transactie op dezelfde rekening wacht tot de eerste klaar is, en leest dan het nieuwe saldo. Door de vaste volgorde wachten twee transacties nooit op elkaar in een kring, dus er is geen deadlock. Dit is wat het TO voorschrijft (§6).
2. **SERIALIZABLE:** PostgreSQL ontdekt het conflict en breekt één transactie af. Dat vraagt een retry-lus rond elke boeking. De fout komt pas bij commit, en onder belasting zijn er veel retries.
3. **Advisory locks per IBAN** (`pg_advisory_xact_lock`). Dat werkt, maar de lock hangt aan een getal en niet aan de rij. Een vergeten lock in andere code valt niet op.
4. **Optimistisch, met een versiekolom op `rekening`.** Bij een conflict faalt de update, en dan is er ook een retry nodig. Het saldo zelf staat niet op de rij (het wordt berekend), dus de versie moet bij elke boeking kunstmatig omhoog.

## Beslissing
We kiezen optie 1:
- `Grootboek.vergrendel(Iban...)` doet `SELECT iban FROM rekening WHERE iban IN (…) ORDER BY iban FOR UPDATE`;
- `LedgerService.boek` roept dat als eerste aan, voor beide kanten van de overboeking, en leest pas daarna het saldoverloop;
- de volgorde ligt in de query (`ORDER BY iban`), niet bij de aanroeper.

## Gevolgen
- Boekingen op dezelfde rekening lopen na elkaar. Boekingen op andere rekeningen lopen gewoon gelijktijdig.
- Ook de ontvangende rekening wordt vergrendeld. Voor nooit rood is dat nu niet nodig, want een bijschrijving verlaagt geen saldo. Maar zodra die rekening zelf betaalt, moet ze een consistent saldo zien. Bovendien houdt het de regel eenvoudig: elke boeking vergrendelt al haar rekeningen.
- De seed schrijft buiten `LedgerService` om en vergrendelt niet. In een scenario gebeurt de seed vóór de betaling, dus ze botsen niet.
- Een lange transactie houdt de rekening vast. `boek` doet daarom niets anders dan vergrendelen, lezen en schrijven.

## Bewaking
- **`GelijktijdigeOverboekingenTests`:** 100 overboekingen kriskras, met deze controles:
  - geen deadlock (alles klaar binnen 60 s);
  - geen andere fouten dan saldo ontoereikend;
  - het totaal blijft gelijk;
  - geen saldo onder € 0,00.
- **`JdbcGrootboekTests`:** `vergrendel` houdt een tweede transactie op dezelfde rekening tegen, totdat de eerste klaar is.
