# ADR-sjabloon (naar Michael Nygard, met Bewaking)

Een ADR legt één beslissing vast, met de context en de gevolgen. Schrijf hem op het **laatste verantwoorde moment**: als een scenario niet verder kan zonder de keuze. Niet eerder, want dan kies je zonder kennis. Niet later, want dan is de keuze al impliciet gemaakt.

Bestand: `<adr-map uit het profiel>/NNNN-korte-titel.md`. Gebruik het volgende vrije nummer.

```markdown
# NNNN. Titel als beslissing geformuleerd

Datum: JJJJ-MM-DD
Status: Voorgesteld | Geaccepteerd | Vervangen door [NNNN](NNNN-...md)

## Context
Welk scenario of welke kracht dwingt deze keuze af? Welke opties waren er?

## Beslissing
Wat we doen, in één of twee zinnen (of een korte opsomming).

## Gevolgen
Wat makkelijker wordt, wat moeilijker wordt, wat we daarvoor accepteren.

## Bewaking
Welk scenario of welke fitness function faalt als we hiervan afwijken.
Bijvoorbeeld: een architectuurtest, een contracttest, een template-test, een scripttest of een rooktest.
```

## Regels

- **Eén beslissing per ADR.**
- **Een geaccepteerde ADR wordt nooit herschreven.** Een nieuwe beslissing krijgt een nieuw nummer. De oude krijgt de status "Vervangen door".
- **Elke ADR noemt zijn Bewaking**, liefst geautomatiseerd. Kan dat (nog) niet, schrijf dan op hoe en wanneer je het controleert.
- **De ADR gaat mee in dezelfde commit** als de code die de beslissing invoert.
- **Jij stelt op, de gebruiker beslist.** Schrijf het concept met status `Voorgesteld` en de opties. De gebruiker zet de status op `Geaccepteerd`.
- **Een nieuwe library, tool of infrastructuurcomponent** is altijd een ADR-kandidaat, geen implementatiedetail.
- **Een ADR die tijdens het bouwen wordt aangevuld** (bijvoorbeeld een extra bewaking) mag je bijwerken zolang de beslissing zelf niet verandert. Noem dat in de commit.
