# 0031. Drivers wachten en controleren het protocol; de DSL beweert

Datum: 2026-10-31
Status: Geaccepteerd

## Context
In het vierlaagse model (werkwijze hoofdstuk 5, ADR 0005) stond dat protocol drivers geen "beweringen over gedrag" bevatten. De drivers gebruiken toch `expect` van Playwright, op drie manieren:
- **Wachten.** De UI-drivers wachten met `expect(locator).toBeVisible()` tot een scherm klaar is.
- **Het protocol controleren.** De REST-driver controleert een status 200 bij een GET en de vorm van een antwoord, bijvoorbeeld een pagina met `transacties` en `volgende`.
- **Een uitkomst beweren.** De REST-driver controleert de 201 van een betaling of een overschrijving.

Daarnaast bewaakte niets de bovenste laag: dat een spec alleen de DSL gebruikt, was een afspraak zonder test (backlog fase 2.5).

Een bewering in een driver heeft vier nadelen:
- Een driver wordt door veel scenario's gebruikt, dus een ingebouwde verwachting geldt voor allemaal. Een scenario dat een weigering verwacht, heeft dan een tweede methode nodig, zoals `probeerOpTeNemen` naast `neemOp`.
- Hetzelfde scenario draait via REST en via de UI. Een bewering in één driver maakt de twee ongelijk.
- Een goedgekeurde verwachting verandert niet zonder ⛔. Een bewering in een driver valt buiten die controle.
- De melding van een driver spreekt geen domeintaal.

Opties:
1. **Elke `expect` uit de drivers.** Zuiver, maar wachten en protocolcontroles gaan dan naar de DSL. Dat is precies de techniek die daar niet hoort.
2. **Wachten en het protocol controleren mag; een uitkomst beweren niet.**

## Beslissing
We kiezen optie 2:
- **Een driver mag wachten** tot het systeem klaar is, ook met `expect(...).toBeVisible()`.
- **Een driver mag het protocol controleren:** de status en de vorm van een antwoord dat hij moet kunnen lezen. Faalt dat, dan faalt de driver meteen, met een duidelijke melding.
- **Een uitkomst die per scenario kan verschillen, beweert de driver niet.** Of een betaling lukt of geweigerd wordt, geeft hij terug, bijvoorbeeld als `Uitkomst`, zoals `zetSimulatiedatum` al doet. De DSL beweert erover.
- **Een spec spreekt alleen de DSL.** Hij importeert alleen uit `dsl/` en gebruikt geen `page`, `request` of `expect`.

## Gevolgen
- De drivers die nu een uitkomst beweren, krijgen een `Uitkomst` als resultaat. Dubbele methodes als `neemOp` en `probeerOpTeNemen` kunnen dan samengaan (backlog fase 3).
- Het onderscheid tussen "protocol" en "uitkomst" is niet automatisch te controleren. Een `expect` in een driver blijft een punt voor de review: kan deze waarde per scenario verschillen?
- Een spec die toch iets direct wil aanspreken, moet via de DSL. Dat kost soms een DSL-methode extra.

## Bewaking
- **`scripts/lagen-check.sh`**, in de commit stage (`make commit-stage` en de frontendjob in CI): een spec importeert alleen uit `dsl/` en gebruikt geen `page`, `request` of `expect`. Faalt ook als er geen specs zijn gevonden.
- **`scripts/test/lagen-check.bats`**, in de bats-stap van `scripts/iac-check.sh`: de controle zelf, met goede en foute specs.
