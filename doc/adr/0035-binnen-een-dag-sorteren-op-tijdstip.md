# 0035. Binnen een dag sorteren op tijdstip

Datum: 2026-11-01
Status: Geaccepteerd; vervangt de sortering en de inhoud van de cursor uit [0011](0011-keyset-paginering-met-cursor.md)

## Context
Scenario "Binnen een dag staat de laatste transactie bovenaan, ook als ze eerder is geboekt" (`e2e/specs/betaalrekening.spec.ts`, losse backlogpunt uit werkwijze hoofdstuk 7). De transactielijst sorteert op `(datum DESC, id DESC)` (ADR 0011). Binnen een dag is dat de volgorde van boeken, niet de volgorde in de tijd. Een betaling van 09:00 die na een betaling van 15:00 geboekt wordt, staat er nu boven. Het scenario is rood: Albert Heijn (09:00) bovenaan in plaats van de bakkerij (15:00).

De cursor van "Toon meer" bevat de positie in die sortering: de datum en het id van de laatste transactie op de pagina. Verandert de sortering, dan moet de cursor mee veranderen. Anders kan een grens midden in een dag een transactie overslaan of dubbel tonen.

`transactie.tijdstip` is verplicht (`NOT NULL`, V4) en hoort bij dezelfde dag als `datum` in Europe/Amsterdam.

Opties:
1. **Sorteren op `(datum DESC, tijdstip DESC, id DESC)`.** De datum blijft de eerste sleutel, zodat de groepering per dag altijd klopt. Het id beslist alleen nog bij een gelijk tijdstip. De cursor bevat datum, tijdstip en id. Een nieuwe index `(iban, datum DESC, tijdstip DESC, id DESC)` vervangt die van ADR 0011.
2. **Sorteren op `(tijdstip DESC, id DESC)`, zonder datum.** Korter, maar dan moet de datum altijd uit het tijdstip volgen. Dat geldt nu, maar niets dwingt het af.
3. **Binnen een pagina sorteren in de frontend.** Klopt niet over de grens van een pagina heen, en de REST-driver zou iets anders zien dan de browser.

## Beslissing
We kiezen optie 1.

- **Sortering:** `ORDER BY t.datum DESC, t.tijdstip DESC, t.id DESC`, en de keyset `(t.datum, t.tijdstip, t.id) < (:datum, :tijdstip, :id)`.
- **Cursor:** de inhoud wordt `datum,tijdstip,id`, met het tijdstip als ISO-8601 met offset. De HMAC gaat over iban en die inhoud, zoals in ADR 0011. Base64url en de handtekening blijven.
- **Index:** een Flyway-migratie vervangt `transactie_rekening_datum` door `(iban, datum DESC, tijdstip DESC, id DESC)`.

Wat ADR 0011 verder vastlegt, blijft gelden: porties van 50, de vorm van de API (`transacties`, `volgende`), 400 bij een onleesbare of aangepaste cursor.

## Gevolgen
- Een cursor van vóór deze wijziging is onleesbaar en geeft 400, net als bij een rotatie van de sleutel. Wie precies tijdens de uitrol op "Toon meer" klikt, ziet één keer de melding van de frontend en herlaadt de rekening.
- De DSL vergelijkt de volgorde binnen een dag nu met het tijdstip. `verwachtTransacties` (S2.2) gaat uit van de volgorde van boeken; de seed van dat scenario boekt in tijdsvolgorde, dus het blijft gelijk.
- De spaarrekening gebruikt hetzelfde endpoint en krijgt dezelfde volgorde.

## Bewaking
- **Acceptatie:** het scenario hierboven, in `rest` en `chromium`.
- **Repositorytest** (`JdbcTransactieRepositoryTests`): twee transacties op één dag, in omgekeerde tijdsvolgorde geboekt, komen in tijdsvolgorde terug, ook over de grens van een pagina met een cursor ertussen.
- **CursorTests:** de cursor bevat het tijdstip en wordt onleesbaar als iemand het aanpast.
