# 0024. Een overboeking heeft twee kanten in één grootboek, alleen via `LedgerService`

Datum: 2026-10-24
Status: Geaccepteerd

## Context
Scenario "Betaling naar een contact" (S7.1, `e2e/specs/betalen.spec.ts`): Jan betaalt € 25,00 aan Sanne. Daarna ziet Jan -25,00 aan Sanne en een saldo van € 475,00, en ziet Sanne +25,00 van Jan. Het is de eerste geldbeweging tussen twee rekeningen van BankSim. Rood gezien: `POST /api/betalingen` gaf 404.

Het TO (§4, §6) vraagt om één grootboek met double-entry:
- elke geldbeweging heeft twee kanten in dezelfde databasetransactie;
- alleen `LedgerService` boekt;
- een databaseconstraint is de laatste verdedigingslinie.

Het origineel maakte daarvoor van elke IBAN een rekening, ook die van bedrijven, met de tabellen `overboeking` en `boeking`.

Zo staat het nu:
- **Tabel `transactie`** (V2, V4) heeft één kant per rij: rekening, bedrag met teken, tegenpartij (naam), tegen-IBAN, type, tijdstip en datum. Het saldo is het openingssaldo plus de som (ADR 0010).
- **De seed** schrijft rijen voor tegenpartijen zonder rekening bij BankSim, zoals de werkgever of de supermarkt (ADR 0003).
- **Lezen:** de transactielijst, zoeken, de cursor (ADR 0011) en de simulatiedatum (ADR 0013) lezen allemaal `transactie`.

Opties:
1. **Twee losse rijen in `transactie`**, in één databasetransactie. Dit is het kleinst. Niets legt vast dat de rijen bij elkaar horen. Een databaseconstraint of de som-invariant is dan niet te controleren.
2. **Het volledige grootboek van het TO nu**:
   - elke tegenpartij wordt een rekening (soort EXTERN);
   - elke bestaande rij krijgt een tegenboeking;
   - de lijst, zoeken en de cursor gaan over op een nieuwe tabel `boeking`;
   - de seed boekt ook alles dubbel.
   
   Dat raakt alle gebouwde slices in één iteratie, en het scenario vraagt er niet om.
3. **Het grootboek groeit vanuit `transactie`:**
   - een nieuwe tabel `overboeking` met type, tijdstip en datum;
   - `transactie` krijgt een optionele verwijzing `overboeking_id`;
   - een betaling tussen twee rekeningen van BankSim is één overboeking (1 databasetransactie) met precies twee transacties (2 banktransacties), op twee verschillende rekeningen, met som 0;
   - een rij zonder overboeking blijft een eenzijdige beweging met de buitenwereld (seed, en later de generator);
   - de leesqueries veranderen niet.

## Beslissing
We kiezen optie 3:
- **V6:** maakt `overboeking` en `transactie.overboeking_id` (nullable, met een verwijzing). Bestaande rijen houden `NULL`.
- **Constraint trigger** (`DEFERRABLE INITIALLY DEFERRED`): bij commit heeft elke geraakte overboeking precies twee transacties, op twee verschillende rekeningen, met som 0.
- **Domein:** `Overboeking` in `bank-domain` maakt de twee kanten (af en bij), elk met de naam en het IBAN van de andere kant als tegenpartij. Een andere vorm is niet te maken.
- **Boeken:** `LedgerService.boek(Overboeking)` in `bank-api` is de enige code die overboekingen en hun transacties schrijft.
- **De betaling zelf:** type Online bankieren, op "vandaag" voor de bank (`SimulationClock`, ADR 0013), met de huidige tijd (FO).

## Gevolgen
- Het saldo, de transactielijst, zoeken en de cursor blijven werken zoals ze werken. De betaling verschijnt bij allebei als gewone transactie.
- Er zijn voorlopig twee soorten rijen: met overboeking (intern) en zonder (buitenwereld). "Elke IBAN is een rekening" (TO §4) is uitgesteld tot de generator (slice 11). Dan kan ADR 0024 vervangen worden.
- **De som-invariant** (alle saldi samen blijven gelijk) geldt voor overboekingen, niet voor bewegingen met de buitenwereld.
- **Nooit rood en locking:** het locken in IBAN-volgorde en nooit rood krijgen een eigen ADR, bij het scenario "Rood staan is niet toegestaan" en het vierde scenario. Deze ADR legt alleen de vorm van het grootboek vast.
- **De seed** blijft eenzijdige rijen schrijven, en raakt `overboeking` niet. Een betaling tussen twee klanten in "Gegeven" loopt straks via de API (`jan.betaalt`), niet via de seed.

## Bewaking
- **Domein:** `Overboeking` heeft altijd twee kanten met som 0. jqwik controleert dat voor willekeurige bedragen en IBAN's, en ook dat dezelfde IBAN aan twee kanten geweigerd wordt.
- **Database** (Testcontainers):
  - een overboeking met één transactie, of met een som ongelijk aan 0, of twee keer dezelfde rekening, faalt bij commit;
  - `LedgerService` boekt beide kanten in één databasetransactie.
- **ArchUnit:** alleen `LedgerService` gebruikt de schrijvende repository van het grootboek.
- **Het scenario "Betaling naar een contact"**, in rest en chromium.
- **Later in deze slice:** de concurrency-test en de jqwik-test op de som van alle saldi (werkwijze §7). Die horen bij de ADR over nooit rood en locking.
