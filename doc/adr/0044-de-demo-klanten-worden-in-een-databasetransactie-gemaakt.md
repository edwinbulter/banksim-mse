# 0044. De demo-klanten worden samen in één databasetransactie gemaakt

Datum: 2026-11-04
Status: Geaccepteerd

## Context
Scenario "Klanten betalen elkaar, van oktober 2021 t/m december 2026" (S11.1, `e2e/specs/infra/huishoudens.spec.ts`).

Rood gezien, in project `infra`: `betaling van jdevries aan een andere klant`, met `Expected: true` en `Received: false`.

ADR 0041 geeft elke demo-klant een eigen databasetransactie: de rekeninghouder, de rekeningen, de transacties en de login. Bestaat de rekeninghouder, dan slaat `DemoData` de klant over. Dat werkt zolang de boekingen van een klant alleen zijn eigen rekeningen raken.

Een betaling tussen klanten raakt twee klanten. Jan betaalt Sanne: dat is één overboeking met twee kanten (ADR 0024), en die kan pas worden geschreven als beide betaalrekeningen bestaan. Bovendien mag de betaling bij een tweede poging niet nog een keer worden geboekt. De vraag is dus hoe `DemoData` deze betalingen precies één keer schrijft, ook als een eerste poging halverwege mislukt, bijvoorbeeld doordat Keycloak weg is.

Opties:
1. **Alle 10 klanten en hun onderlinge betalingen in één databasetransactie.** Mislukt er iets, dan blijft er niets staan en begint de volgende poging opnieuw. Bestaat er een demo-klant, dan is alles er. De logins in Keycloak vallen buiten de databasetransactie en blijven dus staan, maar worden bij een nieuwe poging opnieuw afgehandeld.
2. **Per klant schrijven, en een betaling boeken zodra de tweede klant bestaat.** Elke betaling hoort dan bij de klant van het paar die het laatst wordt gemaakt. Dit houdt ADR 0041 intact en een mislukte poging behoudt de klanten die al klaar zijn. Maar `DemoData` moet dan de rekeningen van eerdere klanten opzoeken, en de regel "welke klant schrijft deze betaling" is lastig te volgen en te testen.
3. **Eerst alle klanten, daarna de betalingen in een eigen databasetransactie, met een controle of ze er al zijn.** Daarvoor is een markering of telling nodig. Een telling op betalingen is onbetrouwbaar, omdat een demo-klant tussen twee pogingen zelf een betaling kan doen. ADR 0041 wees zo'n aparte administratie al af.

## Beslissing
We kiezen optie 1:
- **`DemoData.maak`** schrijft alle 10 klanten en daarna hun onderlinge betalingen in één databasetransactie, gevolgd door de beheerder.
- **Eén controle vooraf:** bestaat er al een demo-klant, dan schrijft `DemoData` geen klanten en geen betalingen. Een halve set kan met deze code niet meer ontstaan.
- **`Generator`** berekent de betalingen over alle klanten samen, op een vaste volgorde en met een eigen seed per klant. Geld dat een klant ontvangt, telt niet mee als ruimte om zelf te betalen: ontvangen geld maakt een saldo alleen hoger, dus "nooit rood" (ADR 0025) blijft gelden.

Dit vervangt de regel "Eén databasetransactie per klant" uit ADR 0041. De rest van ADR 0041 blijft gelden.

## Gevolgen
- **Een mislukte poging begint helemaal opnieuw.** Klanten die al klaar waren, worden opnieuw gemaakt, met nieuwe IBAN's. Dat is geen probleem, want de rollback heeft de oude IBAN's ook verwijderd en niemand kan ze al hebben gebruikt.
- **Logins in Keycloak:** die blijven na een rollback staan. Een nieuwe poging moet er dus mee om kunnen gaan dat de login al bestaat. Dat is al de bedoeling van `zorgVoorKlant`; de test moet het bevestigen.
- **Langere databasetransactie:** de transactie duurt zo lang als de hele K8s Job. De duur meten we bij deze stap. Niemand wacht erop, want de rijen zijn pas na de commit zichtbaar.
- **Bestaande installaties:** die krijgen geen betalingen; gebruik eerst `make uninstall` (ADR 0041). Een installatie van een oudere versie waarop maar een deel van de klanten bestaat, wordt niet meer aangevuld. Dat kan alleen lokaal voorkomen, en ook daar is `make uninstall` de oplossing.
- **`LedgerService` draait hier niet**, zoals bij de inleg (ADR 0042). De som-invariant geldt wel, want de databasetrigger controleert elke overboeking bij de commit.

## Bewaking
- **Scenario:** "Klanten betalen elkaar, van oktober 2021 t/m december 2026", met de bewering dat alle betalingen samen op € 0,00 uitkomen.
- **`DemoDataDatabaseTests`:** een mislukte login laat geen enkele klant en geen enkele betaling achter, de volgende poging maakt alles, en een derde poging verandert niets.
- **`GeneratorTests`:** elke betaling heeft een andere klant als tegenpartij, en geen betaling maakt de rekening van de betaler rood.
- **De databasetrigger** uit `V6__grootboek.sql`: een overboeking die niet in balans is, faalt bij de commit.
