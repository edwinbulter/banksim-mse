# 0025. Nooit rood: het laagste lopende saldo vanaf de uitvoerdatum

Datum: 2026-10-24
Status: Geaccepteerd

## Context
Een betaalrekening mag nooit rood staan (FO, TO §6). Scenario "Rood staan is niet toegestaan" (S7.1) is gebouwd met de kleinste regel: het saldo op de uitvoerdatum moet de betaling dekken (`LedgerService`).

Scenario "Een betaling mag een latere boeking niet rood laten staan" (`e2e/specs/betalen.spec.ts`, `@simulatiedatum`):
- Jan heeft € 500,00;
- op 10 oktober staat een incasso van € 450,00;
- de simulatiedatum is 5 oktober;
- Jan betaalt € 100,00. Dat moet geweigerd worden, want op 10 oktober zou het saldo -€ 50,00 zijn.

Rood gezien, in `simulatiedatum-rest` en `simulatiedatum-chromium`: de betaling ging door (weigering `undefined`).

Bewegingen na "vandaag" bestaan, omdat de simulatiedatum terug kan (ADR 0013) en omdat de seed, en later de generator, vooruit boekt. Een betaling op dag D verlaagt het saldo op D én op elke dag daarna.

Opties:
1. **Het saldo op de uitvoerdatum** (nu). Dat mist latere afschrijvingen, zoals het scenario laat zien. Optie 1 past bij de lezing "de bank staat echt op de simulatiedatum, en wat daarna komt is nog niet gebeurd". Maar de latere boekingen blijven in de database staan. Zet de beheerder de datum vooruit (S3.1), dan worden ze zichtbaar en kan het saldo alsnog negatief worden. Met de getallen van het scenario wordt dat -€ 50,00. Dat breekt "nooit rood" (FO, TO §6). De generator boekt bovendien expres tot en met 31 december 2026 (FO), dus zulke boekingen bestaan ook zonder simulatiedatum.
2. **Het saldo na de laatste boeking.** Dat mist een dip tussendoor: een incasso op 10 oktober en het salaris op 25 oktober geven samen een positief eindsaldo, maar daartussen staat de rekening rood.
3. **Het laagste lopende saldo vanaf de uitvoerdatum.** Neem het saldo aan het eind van D en van elke latere dag met een beweging. Het laagste daarvan moet de betaling dekken.
4. **Geen betalingen toestaan vóór de laatste beweging.** Te streng: dan kan Jan met de simulatiedatum terug in de tijd niets meer betalen.

## Beslissing
We kiezen optie 3. Het TO nam die beslissing vooraf, en het scenario maakt hem hier zichtbaar:
- **Domein:** `SaldoVerloop` in `bank-domain` is het saldo aan het eind van D, plus de bewegingen per latere datum. `NooitRoodRegel` berekent het laagste lopende saldo en weigert als het bedrag dat overschrijdt.
- **Database:** `Grootboek.saldoVerloop(iban, D)` levert het verloop met twee query's, in de databasetransactie van de boeking. De eerste geeft het saldo op D zoals nu; de tweede de latere bewegingen, opgeteld per datum.
- **`LedgerService.boek`** past de regel toe op de betalende rekening, in dezelfde databasetransactie als het schrijven.
- **De melding** wordt die van het origineel: "Saldo ontoereikend: deze betaling zou het saldo onder € 0,00 brengen, nu of op een latere datum."

## Gevolgen
- Een betaling kan worden geweigerd terwijl het saldo van vandaag groot genoeg lijkt. De melding noemt daarom "op een latere datum".
- De regel leest alle latere bewegingen van één rekening. Bij de huidige omvang is dat klein. De generator (slice 11) gaat tot en met 31 december 2026, en ook dan blijft het per rekening beperkt.
- Gelijktijdige betalingen kunnen de regel nog omzeilen. Locking krijgt een eigen ADR, met de concurrency-test uit werkwijze §7.
- De ontvangende kant wordt niet gecontroleerd: een bijschrijving maakt het saldo nooit lager.

## Bewaking
- **jqwik op `NooitRoodRegel`:** voor willekeurige verlopen en bedragen geldt: wordt de betaling toegestaan, dan is elk lopend saldo na de betaling minstens € 0,00. Wordt ze geweigerd, dan was er een dag die eronder zou komen.
- **Unittests op `NooitRoodRegel`:** precies het laagste saldo mag; een cent meer niet; een dip tussen twee latere boekingen telt mee.
- **`JdbcGrootboekTests`:** het verloop vanaf een datum, met de bewegingen per latere dag opgeteld.
- **Scenario's:** "Rood staan is niet toegestaan" en "Een betaling mag een latere boeking niet rood laten staan".
