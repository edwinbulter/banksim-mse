# 0027. Een betaling heeft een Idempotency-Key; dezelfde sleutel boekt één keer

Datum: 2026-10-25
Status: Geaccepteerd

## Context
Scenario "Dubbel verzonden betaling wordt één keer geboekt" (S7.1, CFR, `e2e/specs/betalen.spec.ts`): Jan betaalt € 25,00 aan Sanne en verstuurt de bevestiging twee keer. In de API is dat twee keer hetzelfde verzoek, in de browser een dubbelklik op "Bevestigen".

Rood gezien, in rest en chromium: het saldo van Jan was € 450,00 in plaats van € 475,00. De betaling is dus twee keer geboekt. In chromium leverde de dubbelklik twee POST's op.

Een herhaald verzoek is normaal:
- een dubbele klik;
- een time-out waarna de app of de klant het opnieuw probeert;
- een proxy die een verzoek herhaalt.

De server moet het tweede verzoek herkennen. Voor de klant mag er niets bijkomen.

Opties:
1. **Header `Idempotency-Key`.** De app maakt per controlescherm één willekeurige sleutel (UUID) en stuurt die met elke poging mee. De server bewaart per gebruiker de sleutel, een hash van het verzoek en het antwoord, in dezelfde databasetransactie als de boeking. Een herhaling met dezelfde sleutel krijgt het bewaarde antwoord, zonder nieuwe boeking. Het TO deed dit (§6).
2. **Alleen in de frontend de knop uitschakelen** na de eerste klik. Dat helpt niet bij een herhaling na een time-out of door een proxy, en ook niet voor andere clients. De CFR geldt voor de server.
3. **Dubbele betalingen zelf herkennen**: dezelfde rekening, ontvanger en hetzelfde bedrag binnen een paar minuten. Dat weigert ook twee echte, bewuste betalingen van € 25,00 aan Sanne.
4. **Twee stappen:** eerst een betaalopdracht aanmaken (die krijgt een id van de server), dan die opdracht bevestigen. Dat is een ander API-ontwerp, met een extra verzoek en een opdracht die blijft liggen als de klant afhaakt.

## Beslissing
We kiezen optie 1:
- **De header is verplicht** voor `POST /api/betalingen`. Zonder sleutel antwoordt de server 400 (probleemtype `sleutel-ontbreekt`). Zo kan geen client per ongeluk zonder bescherming betalen.
- **Opslag:** tabel `idempotency_sleutel` (V7), met de sleutel, de gebruikersnaam en de hash van het verzoek, en primaire sleutel (sleutel, gebruikersnaam). Het antwoord staat er niet in: alleen geslaagde betalingen blijven bewaard, en hun antwoord is altijd 201.
- **Volgorde:** `BetaalService` doet `INSERT … ON CONFLICT DO NOTHING` als eerste stap in de transactie van de boeking.
  - Een gelijktijdige tweede poging wacht op de unieke index tot de eerste klaar is.
  - Daarna leest ze het bewaarde antwoord.
  - Wordt de eerste poging geweigerd (bijvoorbeeld saldo ontoereikend), dan rolt de transactie terug, ook de sleutel. Een nieuwe poging wordt dan opnieuw beoordeeld.
- **Dezelfde sleutel met een ander verzoek** (een ander bedrag, een andere ontvanger) geeft 422, met probleemtype `sleutel-al-gebruikt`.
- **De frontend** maakt de sleutel bij het openen van het controlescherm. Een dubbelklik stuurt twee keer dezelfde sleutel.
- **Bewaarduur:** voorlopig onbeperkt. Opruimen staat als backlogpunt in werkwijze §7.

## Gevolgen
- Elke client van `POST /api/betalingen` moet een sleutel meesturen, ook de REST-driver bij gewone betalingen.
- Een sleutel geldt per gebruiker. Twee klanten met toevallig dezelfde UUID zitten elkaar niet in de weg.
- De tabel groeit met elke betaling, tot er wordt opgeruimd.
- Het antwoord op een herhaling is gelijk aan het eerste: 201.

## Bewaking
- **Het scenario** "Dubbel verzonden betaling wordt één keer geboekt", in rest (twee POST's met dezelfde sleutel) en chromium (dubbelklik).
- **Controllertests:**
  - dezelfde sleutel twee keer geeft één overboeking en twee keer 201;
  - zonder sleutel 400;
  - dezelfde sleutel met een ander bedrag 422.
- **Repositorytest** (Testcontainers): twee gelijktijdige transacties met dezelfde sleutel. De tweede wacht, en ziet daarna het antwoord van de eerste.
- **Frontendtest:** twee klikken op "Bevestigen" sturen dezelfde sleutel.
