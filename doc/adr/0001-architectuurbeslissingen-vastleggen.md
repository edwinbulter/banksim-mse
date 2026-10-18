# 0001. Architectuurbeslissingen vastleggen in ADR's

Datum: 2026-10-17
Status: Geaccepteerd

## Context
Het origineel nam alle beslissingen vooraf in een technisch ontwerp. In BankSim-MSE ontstaan beslissingen pas als een slice erom vraagt (doc/werkwijze-mse.md, hoofdstuk 6). Zonder vastlegging raakt de reden achter een keuze zoek, en wordt een keuze stilletjes losgelaten.

## Beslissing
Elke architectuurbeslissing krijgt een ADR in `doc/adr/NNNN-titel.md` volgens het template van Michael Nygard, aangevuld met een paragraaf **Bewaking**. Een ADR wordt niet herschreven; een nieuwe beslissing krijgt een nieuw nummer en zet de oude op "Vervangen door".

## Gevolgen
- Beslissingen zijn terug te vinden met hun context en alternatieven.
- De ADR gaat mee in dezelfde commit als de code die de beslissing invoert.
- Nummers worden uitgegeven in de volgorde van het werkwijzedocument; een nummer kan dus even openstaan (0003 volgt in slice 1).

## Bewaking
Definition of Done (doc/werkwijze-mse.md, hoofdstuk 12): een slice is pas klaar als elke beslissing uit die slice een ADR met bewaking heeft.
