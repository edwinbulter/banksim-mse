# Stap 0: walking skeleton, test-first

Een walking skeleton is de dunste versie van het systeem die van begin tot eind werkt. Hij zit in de echte doelomgeving en draait door de echte pipeline. Functioneel doet hij bijna niets; bijvoorbeeld: de startpagina toont de versie van de API. Het doel is dat elke laag, en de route ertussen, één keer werkt. Daarna heeft elke volgende feature een vangnet.

## Volgorde

1. **Buitenste lus: één scenario.** Schrijf het eerste acceptatiescenario, bijvoorbeeld "De startpagina toont de versie", in de vier lagen: spec, DSL en drivers. Draai het tegen de doelomgeving. Het faalt, en in deze fase is dat de goede reden: er ís nog niets geïnstalleerd. Laat de foutmelding zien, bijvoorbeeld een 404 van de ingress of een connection refused.
2. **Binnenste lussen: elk onderdeel eerst een falende test.**
   - De API geeft de versie terug: een unit- of slicetest.
   - De UI toont de versie: een componenttest.
   - De deployment rendert in de juiste namespace, op de juiste host en zonder cluster-brede resources: template-tests.
   - Het installatiescript weigert een verkeerde doelomgeving: een scripttest met nepcommando's.
3. **Buitenste lus groen.** Installeer lokaal met hetzelfde script dat CI gebruikt. Het scenario slaagt in alle drivers.
4. **De pipeline.**
   - **Commit stage:** alle snelle tests, zonder omgeving. Hier worden de artefacten één keer gebouwd.
   - **Acceptance stage:** start alleen als de commit stage groen is. Installeert precies de artefacten uit de commit stage in een wegwerpomgeving en draait de acceptatietests.

## De pipeline zelf controleren

De pipeline is het testharnas en kan zichzelf niet test-first testen. Controleer hem daarom empirisch:

- Zie hem **minstens één keer rood**, en kijk of de juiste stage faalt. Bij een rode commit stage hoort de acceptance stage niet te starten.
- Maak hem daarna groen. Een pipeline die je nooit rood hebt gezien, bewijst niets.
- De eerste runs falen vaak op verschillen tussen lokaal en CI: bestandsrechten, architectuur (arm64 of amd64), timing van een verse omgeving. Dat is precies waarvoor ze er zijn. Leg elke oorzaak die niet uit de code af te leiden is vast als valkuil, in het profiel of in een stack-referentie.

## Eerste ADR's

- ADR's vastleggen: het proces zelf.
- De deployment pipeline en de doelomgeving: één keer bouwen, en lokaal en in CI dezelfde scripts.
- De keuze voor de acceptatietesttool en de DSL.
- Eventuele afspraken over de doelomgeving, zoals een eigen namespace naast andere apps.
- Het testgereedschap voor de infrastructuur, als dat buiten de standaardstack valt.

## Klaar als

- De commit staat op de hoofdbranch, en de commit stage en de acceptance stage zijn groen.
- De app draait in de doelomgeving en is met de hand te gebruiken.
- De pipeline is minstens één keer rood geweest en heeft dan niet gedeployed.
- Er is een korte instructie waarmee de gebruiker zelf kan nalopen wat er gebouwd is: elke test groen zien en één keer rood zien.
