---
name: uitleg
description: Legt een deel van het systeem uit aan een developer die er verder aan moet werken - de functionaliteit, de route door de code, het ontwerp, de kritische punten en waar een wijziging begint. Haalt alles uit de actuele specs, code, ADR's en het projectprofiel, en schrijft niets weg. Gebruik deze skill bij /uitleg, en als de gebruiker vraagt hoe een slice, scenario, feature, module of bestand werkt, wat je moet weten voordat je er iets aan verandert, of om ingewerkt te worden in een deel van de code.
---

# Uitleg voor de developer

Deze skill legt uit; hij verandert niets. Het doel is dat de developer na het lezen zelf een wijziging in dit deel kan beginnen, en weet waar het oppassen is.

## 1. Het onderwerp

Het argument noemt wat je uitlegt: een slice (`slice 4`), een scenario of spec, een functie in domeintaal (`betalen`), een module (`bank-bff`) of een bestand. Is het onduidelijk of te breed, zoals "het hele systeem", stel dan één vraag om het af te bakenen.

## 2. Bronnen, in deze volgorde

1. **Het projectprofiel** (`CLAUDE.md`): de lagen, de paden en de projectvalkuilen.
2. **De specs:** de scenario's van het onderwerp. Zij beschrijven wat het systeem doet.
3. **De code:** volg één verzoek van het begin tot het eind, door de DSL, de driver en elke laag van het systeem. Lees de bestanden echt; raad niet op naam.
4. **De unittests** van de klassen op de route: wat ze afdekken, en wat niet.
5. **De ADR's** die het onderwerp raken.
6. **De Git-geschiedenis** van de belangrijkste bestanden (`git log --oneline -- <pad>`): de kopjes "Rood gezien" en "Niet geverifieerd" in de commitberichten.

## 3. Opbouw

Gebruik deze kopjes, in deze volgorde. Houd het geheel op één tot twee schermen.

1. **Functionaliteit.** Wat de gebruiker kan, in domeintaal, met de scenario's als bron (`spec:regel`). Noem ook wat het onderwerp bewust níet doet, als de specs of de backlog dat zeggen.
2. **Route door de code.** Eén verzoek als genummerde lijst, van de gebruiker tot de database en terug. Per stap: `bestand:regel`, en in één zin wat daar gebeurt.
3. **Ontwerp.** De belangrijkste klassen of componenten en hun verantwoordelijkheid. Waarom het zo is opgebouwd, met de ADR erbij. Wat de fitness functions (ArchUnit en dergelijke) hier bewaken.
4. **Kritische punten.** Waar een wijziging het makkelijkst misgaat:
   - beveiliging: wie mag wat, en waar wordt dat gecontroleerd;
   - gedeelde toestand, tijd en transacties;
   - DSL-methodes of drivers die ook andere specs gebruiken;
   - de valkuilen uit het profiel die hier gelden;
   - wat níet getest is, met de reden als die bekend is.
5. **Hoe je het wijzigt.** Waar een volgende wijziging begint: welk scenario of welke spec, welke unittest het eerst rood wordt, en welke commando's uit het profiel je draait.

Sluit af met twee of drie vragen waarop de developer kan doorgaan, bijvoorbeeld "Wil je de route door de UI-driver zien?".

## 4. Regels

- **Alleen wat je gezien hebt.** Elke bewering over de code heeft een `bestand:regel`. Kon je iets niet vaststellen, zeg dat dan, en raad niet.
- **Getest of niet:** zeg bij een kritisch punt of een test het afdekt, en welke.
- **Leg het *waarom* uit, niet alleen het *wat*.** Code voorlezen helpt niet; de keuze erachter wel.
- **Niets wegschrijven.** De uitleg komt in het gesprek, niet in een document dat kan verouderen.
- **Niets wijzigen.** Zie je een fout of een ontbrekende test, noem hem dan bij de kritische punten. Repareren gaat via de skill `atdd`, met eerst een rode test.
- **Taal:** die van het profiel, met de domeintermen uit de code.
