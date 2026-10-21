# 0011. Transactielijst per 50 met keyset-paginering en een cursor

Datum: 2026-10-21
Status: Geaccepteerd

## Context
Scenario "Toon meer laadt per 50 transacties" (S2.2, `e2e/specs/betaalrekening.spec.ts`): de betaalrekening toont de nieuwste 50 transacties, en "Toon meer" laadt de volgende 50. Tussen twee keer "Toon meer" kan er een transactie bijkomen. De grens van 50 kan midden in een dag vallen.

Opties:
1. **Offset** (`LIMIT 50 OFFSET 100`). Dit is eenvoudig, maar er zijn twee nadelen. Een nieuwe boeking schuift de lijst op, waardoor een transactie dubbel verschijnt of ontbreekt. En de database leest de overgeslagen rijen toch.
2. **Keyset** op `(datum DESC, id DESC)`: "de 50 na de laatste die ik zag". Dit is stabiel bij nieuwe boekingen en gebruikt de bestaande index `(iban, datum DESC, id DESC)`. De positie gaat als cursor heen en weer. Er zijn drie varianten:
   - a. de cursor leesbaar maken (`?na=2026-01-17,51`);
   - b. de cursor ondoorzichtig maken (base64url), zonder handtekening;
   - c. de cursor ondoorzichtig maken én ondertekenen met HMAC-SHA256, met een geheime sleutel. Zo doet het origineel het (TO §3.2, §11 A01).

De eigenaarscontrole zit al in de query: andermans IBAN geeft 404. Een gemanipuleerde cursor kan dus geen andermans data tonen. Een handtekening (2c) beschermt dus vooral tegen twee dingen:
- clients die gaan leunen op de opbouw van de cursor;
- onzinnige invoer die tot in de query komt.

## Beslissing
- **Paginering.** Keyset op `(datum DESC, id DESC)`, met porties van 50. De repository vraagt 51 rijen op. Een 51e rij betekent dat er nog meer is.
- **API.** `GET /api/rekeningen/{iban}/transacties[?na=<cursor>]` geeft:
  ```json
  { "transacties": [ … ], "volgende": "<cursor>" }
  ```
  `volgende` is `null` aan het eind van de lijst. De namen zijn Nederlands, zoals de rest van de API. Het origineel gebruikt `items`, `nextCursor` en `cursor`.
- **Cursor (2c).** De cursor bevat de `datum` en de `id` van de laatste transactie op de pagina, in base64url, met daarbij een HMAC-SHA256 over iban, datum en id.
  - De sleutel staat in het Secret `bank-api`. `install.sh` maakt hem één keer aan, net als de wachtwoorden (ADR 0009). Hij staat nooit in Git of in `values.yaml`.
  - Zonder sleutel, of met een sleutel van minder dan 32 tekens, start bank-api niet.
  - Een onleesbare cursor, een aangepaste cursor of een cursor van een andere rekening geeft 400.

## Gevolgen
- "Toon meer" blijft correct als er een transactie bijkomt, en ook bij een grens midden in een dag.
- Er is geen "spring naar pagina 7" en geen totaal aantal pagina's. De schermen hebben dat ook niet nodig.
- Het antwoord verandert van een lijst in een pagina. Dat is een breaking change voor de frontend van S2.1, die in dezelfde commit meegaat.
- Er komt een extra sleutel in het Secret `bank-api` bij, en daarmee sleutelbeheer. Als de sleutel wordt vervangen, worden open cursors ongeldig. De klant opent de rekening dan opnieuw.

## Bewaking
- Het scenario "Toon meer laadt per 50 transacties", in alle protocoldrivers. De grens van 50 valt daarin midden in een dag, en elke transactie is uniek. Een dubbele of ontbrekende transactie laat het scenario falen.
- Repositorytests tegen een echte PostgreSQL (Testcontainers) voor:
  - de volgorde bij dezelfde datum;
  - precies 50 transacties: geen `volgende`;
  - een nieuwe boeking tussen twee pagina's.
- Controllertests:
  - een ongeldige cursor geeft 400;
  - ook een aangepaste cursor of een cursor van een andere rekening geeft 400;
  - zonder geldige sleutel start de cursor niet.
- Scripttest (bats): `install.sh` maakt de sleutel één keer aan en overschrijft hem niet bij een volgende installatie.
