# 0010. Bedragen als BigDecimal, en als string in JSON

Datum: 2026-10-20
Status: Geaccepteerd

## Context
Het overzicht toont het saldo € 1.842,17. Floating point (`double` in Java, `number` in JavaScript) kan zulke bedragen niet exact weergeven. Het origineel gebruikt `BigDecimal` met twee decimalen en `HALF_EVEN`, en bedragen als string in JSON.

## Beslissing
- In Java: een waardeobject `Bedrag` in `bank-domain` rond `BigDecimal`, altijd 2 decimalen, afronding `HALF_EVEN`. Geen `double` of `float` in geldcode.
- In de database: `numeric(15,2)`.
- In JSON: een string met punt als decimaalteken, bijvoorbeeld `"1842.17"`.
- In de frontend: geen `number` voor bedragen; voor weergave wordt de string omgezet naar Nederlandse notatie ("€ 1.842,17") zonder te rekenen. Rekenen in de frontend komt pas als een scenario erom vraagt, en dan met `big.js` (zoals het origineel), met een eigen ADR.

## Gevolgen
- Bedragen zijn overal exact; de DSL en de drivers gebruiken dezelfde stringnotatie.
- Iets meer code voor de omzetting naar weergave.

## Bewaking
- jqwik-properties voor `Bedrag`: altijd 2 decimalen, en de stringnotatie overleeft heen en terug. `a + b − b = a` komt erbij zodra `Bedrag` kan optellen (slice 2).
- ArchUnit: geen `double` of `float` in `bank-domain` en in de geldcode van `bank-api`.
- ESLint: geen `parseFloat`, `parseInt`, `Number(…)`, `Number.parse…` of unaire `+` in de frontend.
