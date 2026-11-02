# 0038. De beheerder leest via eigen endpoints onder `/api/beheer`

Datum: 2026-11-02
Status: Geaccepteerd

## Context
Scenario "De beheerder bekijkt de betaalrekening van een klant alleen-lezen" (`e2e/specs/beheer.spec.ts`, S10.1). Het FO "Admin scherm": klikken op een rekeninghouder toont zijn rekeningen met alle transacties en de zoekfunctie, alleen-lezen. Betalen, Opnemen en Inleggen zijn verborgen.

Een klant leest zijn transacties via `GET /api/rekeningen/{iban}/transacties`. Dat endpoint geeft alleen de rekeningen van de ingelogde gebruiker: `TransactieRepository.vanRekening` krijgt de gebruikersnaam mee, en een rekening van een ander is een 404 (S6.1). Die regel bewaakt de privacy van de klanten.

De beheerder moet dezelfde gegevens zien, van elke klant.

Opties:
1. **Eigen endpoints onder `/api/beheer/**`**, met `@PreAuthorize("hasRole('beheerder')")`. Bijvoorbeeld `GET /api/beheer/rekeningen/{iban}/transacties`, met dezelfde pagina's, cursor en zoekfilter als bij de klant. Ze lezen alleen; er komt onder `/api/beheer` geen betaling of overschrijving.
2. **De bestaande endpoints ook voor de beheerder openzetten**: zonder controle op de eigenaar als de gebruiker de rol beheerder heeft. Geen nieuwe endpoints, maar in elke query van de klant komt een uitzondering voor de beheerder. Een fout in die uitzondering laat een klant de rekeningen van een ander zien.

## Beslissing
We kiezen optie 1.

- **Wat een klant ziet, verandert niet.** De endpoints van de klant blijven alleen de eigen rekeningen geven. Er komt geen uitzondering in.
- **Alles van de beheerder staat onder `/api/beheer/**`**, met de rol beheerder (ADR 0022). Die endpoints lezen alleen. Wat de beheerder mag veranderen, is alleen de simulatiedatum (S10.2).
- **De logica wordt gedeeld, de toegang niet.** De query voor de transacties krijgt een variant zonder eigenaar, die alleen de controller onder `/api/beheer` gebruikt. Paginering, cursor en zoekfilter zijn dezelfde code.
- **De frontend** gebruikt voor de beheerder hetzelfde scherm als voor de klant, onder de route `/beheer/rekeningen/{iban}`, achter de portier `alleenBeheerder` (ADR 0037). Daar vraagt het scherm de endpoints onder `/api/beheer`, en zijn Betalen, Opnemen en Inleggen verborgen.
- **Betalen blijft van de klant.** `POST /api/betalingen` boekt alleen vanaf een eigen rekening. Een beheerder heeft geen rekeningen, dus voor hem wordt elke betaling geweigerd.

## Gevolgen
- Per scherm dat de beheerder leest, komt er een endpoint bij in het contract (ADR 0034): eerst de transacties van de betaalrekening, later de spaarrekening.
- Het scherm van de betaalrekening krijgt een modus "alleen-lezen", waarin het een ander pad aanroept.
- Een nieuw endpoint onder `/api/beheer` vraagt de rol beheerder; dat dwingen `ToegangPerEndpointTests` en `AlleEndpointsTests` al af.

## Bewaking
- **Acceptatie:** "De beheerder bekijkt de betaalrekening van een klant alleen-lezen", in `rest` en `chromium`. Bij REST wordt een betaling als beheerder geweigerd; in de browser is er geen knop Betalen.
- **Toegang:** `ToegangPerEndpointTests` en `AlleEndpointsTests`: een klant krijgt op `/api/beheer/**` een 403.
- **Privacy van de klant:** de bestaande tests (S6.1) blijven bewijzen dat een klant een rekening van een ander niet ziet.
- **Controllertest** van het nieuwe endpoint, tegen het contract.
