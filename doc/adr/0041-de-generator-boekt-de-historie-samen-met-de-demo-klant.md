# 0041. De generator boekt de historie samen met de demo-klant, met een vaste seed

Datum: 2026-11-03
Status: Geaccepteerd

## Context
Scenario "Elke klant heeft elke maand transacties, van oktober 2021 t/m december 2026" (S11.1, `e2e/specs/infra/huishoudens.spec.ts`). De data loopt tot en met 31 december 2026 (FO).

Rood gezien, in project `infra`: `maanden zonder transacties bij jdevries`, met alle 63 maanden in de lijst.

ADR 0040 maakt de klanten en hun rekeningen, zonder transacties. ADR 0024 legt al vast dat een rij zonder overboeking een eenzijdige beweging met de buitenwereld is, "seed, en later de generator". Er zijn nog drie vragen over die samen één beslissing vormen.

Opties per vraag:

**Waar de transacties langs gaan**
1. **Rechtstreeks in de database, via de opslag van `bank-seed`.** De generator bepaalt zelf de boekingen, met de regels uit `bank-domain`. Dezelfde weg als de testdata (ADR 0003), en de databasegebruiker `bank_seed` mag dat al.
2. **Via de API van `bank-api`.** Een echt pad door het systeem, maar er is geen endpoint voor een salaris of incasso van buiten, en duizenden aanroepen met inloggen maken een installatie veel trager. Een endpoint alleen hiervoor zou een test-only endpoint zijn.

**Hoe de generator weet dat de transacties er al zijn**
1. **Ze horen bij het aanmaken van de klant.** Rekeninghouder, rekeningen, transacties en login staan in één databasetransactie (ADR 0040). Bestaat de rekeninghouder, dan is alles er. Geen extra administratie.
2. **Een aparte markering of telling per klant.** Dat kan een bestaande klant later aanvullen, maar die code is alleen nodig voor een bestaande installatie, en die hoeft niet behouden te blijven.

**Hoe de data steeds hetzelfde blijft**
1. **Een vaste seed in de generator.** Dezelfde code geeft dezelfde data, op elke installatiedatum. Dat is ook de basis voor de golden master van een latere stap.
2. **Willekeurig per installatie.** Eenvoudiger, maar demo's en fouten zijn dan niet te herhalen.

## Beslissing
We kiezen in alle drie de gevallen optie 1:
- **`Generator`** in `bank-seed` maakt per klant de boekingen van oktober 2021 t/m december 2026, uit vaste gegevens per klant (`DemoKlanten`). Deze stap heeft nog geen toeval en dus nog geen seed nodig; de vaste seed komt met de eerste boeking die toeval gebruikt. Bedragen zijn centen of `Bedrag`, nooit `double` (ADR 0010).
- **Eenzijdig.** Een werkgever of verhuurder heeft geen rekening bij BankSim, dus zo'n boeking is een rij zonder overboeking (ADR 0024). Betalingen tussen huishoudens komen later, als overboeking met twee kanten.
- **Eén databasetransactie per klant.** `DemoData` schrijft de transacties samen met de rekeningen, vóór de login. Een mislukte poging laat niets achter.
- **Einddatum vast.** Tot en met 31 december 2026, ook als dat na de installatiedatum ligt (FO); een klant ziet die boekingen pas als de datum zo ver is (ADR 0013).
- **Wegschrijven.** Met de bestaande `Transacties.boekAlle`. Bulk-insert met `COPY`, zoals in het TO, volgt pas als een meting erom vraagt.

Deze stap boekt alleen het maandelijkse salaris of pensioen en de huur of hypotheek. De andere soorten uit het FO krijgen hun eigen stap.

## Gevolgen
- **Bestaande installaties:** een demo-klant die er al is, krijgt geen transacties. Lokaal is daarom eenmalig `make uninstall` nodig; CI installeert altijd vers. Dezelfde beperking geldt voor elke latere uitbreiding van de generator, tot een commando bestaat dat de demo-data opnieuw maakt.
- **Nooit rood:** de eerste versie liet een demo-rekening tijdelijk negatief staan. Sinds de volgende stap gebruikt de generator een beginsaldo en controleert hij elke klant met `NooitRoodRegel`; een profiel dat rood zou gaan, laat de K8s Job falen.
- **Toekomstige boekingen:** boekingen na vandaag bestaan al in de database. Daarom houdt de generator straks een buffer aan, zoals het TO beschrijft (ADR 0025).
- **Duur:** de K8s Job wordt langzamer naarmate er meer soorten boekingen komen. Dat meten we bij elke stap.
- **Twee kopieën van de namen:** de namen staan in `DemoKlanten` en in de DSL (`dsl/huishoudens.ts`); het scenario controleert dat ze bij elkaar passen.

## Bewaking
- **Scenario:** "Elke klant heeft elke maand transacties, van oktober 2021 t/m december 2026".
- **Unittests van `Generator`:** twee keer genereren geeft dezelfde boekingen; elke maand heeft salaris of pensioen, en huur of hypotheek als het profiel die heeft; het eerste en laatste bedrag van een klant kloppen.
- **`DemoDataDatabaseTests`:** een mislukte login laat ook geen transacties achter, en een tweede keer boekt niets bij.
- **`GeenDrijvendeKommaTests` en `GeenSysteemtijdTests`:** de generator gebruikt geen `double` en leest de systeemtijd niet.
