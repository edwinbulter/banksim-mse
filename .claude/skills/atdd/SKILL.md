---
name: atdd
description: Acceptance test-driven development volgens Dave Farley (Modern Software Engineering) - double-loop outside-in TDD, het vierlaagse acceptatietestmodel (testcase, DSL, protocol drivers, systeem), ADR's met bewaking en de deployment pipeline als scheidsrechter. Gebruik deze skill bij elk bouwwerk in een project dat zo werkt - een scenario of acceptatietest toevoegen, aan een nieuwe slice of feature beginnen, "volgende stap", een ADR opstellen, een walking skeleton opzetten, een rode pipeline onderzoeken - en ook als de gebruiker ATDD, BDD, double loop, outside-in of Farley noemt, of een nieuw project op deze manier wil starten. Leest eerst het ATDD-profiel van het project (CLAUDE.md of doc/atdd-profiel.md).
---

# ATDD volgens Farley

Deze skill is een werkinstructie. Hij beschrijft *hoe* je bouwt. *Wat* je bouwt en *waarmee* staat in het projectprofiel en de backlog van het project.

## 0. Eerst: het projectprofiel

1. Zoek het profiel, in deze volgorde:
   - een sectie "ATDD-profiel" in `CLAUDE.md`;
   - anders het bestand `doc/atdd-profiel.md`.
2. Uit het profiel haal je:
   - de commando's voor de commit stage, de acceptatie en één scenario;
   - waar specs, DSL, drivers en ADR's staan;
   - de doelomgeving en wat daar verboden is;
   - de commitafspraken;
   - de projectvalkuilen;
   - waar de backlog staat.
3. **Geen profiel?** Stel het samen met de gebruiker op met [references/profiel-sjabloon.md](references/profiel-sjabloon.md). Neem geen paden of commando's aan die je niet in de repo hebt gezien.
4. **Nog geen pipeline of acceptatietest?** Begin met stap 0, het walking skeleton: [references/walking-skeleton.md](references/walking-skeleton.md). Bouw geen functionaliteit zonder vangnet.
5. **Waar staan we?** Leid de voortgang af uit de repo: bestaande specs, `@wip`-tags, ADR's en de status in de README. Zoek daarna de volgende stap op in de backlog die het profiel noemt.

Laad een stack-referentie (`references/stack-*.md`) pas als je in die laag werkt.

## 1. De iteratie

Eén iteratie is één voorbeeld, van scenario tot groene pipeline. ⛔ markeert een stoppunt. Daar stop je en wacht je op de gebruiker.

1. **Voorbeeld kiezen.** Kies één concreet voorbeeld uit de backlog of het functionele ontwerp. Schrijf het als scenario in domeintaal, volgens de stappen Gegeven, Als en Dan, met concrete waarden.
   ⛔ Leg het scenario voor. De gebruiker keurt het scenario en de verwachte waarden goed of past ze aan. De mens is eigenaar van het voorbeeld.
2. **Rood om de goede reden.** Draai het scenario en laat de foutmelding zien.
   - Goede reden: de functionaliteit ontbreekt, bijvoorbeeld een 404, een element dat niet bestaat of een verkeerde waarde.
   - Verkeerde reden: een typefout, een fout in de DSL of een omgeving die niet draait. Repareer eerst de test.
3. **DSL en drivers.** Voeg alleen de DSL-methodes en drivermethodes toe die dit scenario nodig heeft. Volgorde:
   1. de driver die het dichtst bij de logica zit, meestal REST;
   2. daarna de UI-driver.
4. **Binnenste lus, outside-in.** Begin bij de buitenste klasse die het scenario raakt, bijvoorbeeld een controller of een component. Per stap:
   1. **Rood.** Schrijf een unittest en laat zien dat hij faalt, met de melding erbij. Collaborators die nog niet bestaan, vervang je door een test double. Zo ontdek je hun interface vanuit het gebruik.
   2. **Groen.** Schrijf de kleinste code die de test laat slagen.
   3. **Refactor.** Alleen op groen, als aparte stap. Refactor ook de tests.

   Ga zo een laag naar binnen tot alle lagen echt bestaan. Test doubles blijven alleen staan op echte grenzen, zoals tijd, externe systemen of het netwerk.
5. **Architectuurkeuze nodig?** Schrijf dan een ADR-concept volgens [references/adr-sjabloon.md](references/adr-sjabloon.md), met opties, gevolgen en Bewaking.
   ⛔ De gebruiker beslist en zet de status op Geaccepteerd. Een nieuwe library of tool is altijd een ADR-kandidaat.
6. **Lokaal verifiëren.**
   1. Draai het commando voor de commit stage uit het profiel.
   2. Installeer de nieuwe versie en draai de acceptatietests. Controleer aan de versie dat je test wat je net gebouwd hebt.
   3. Draai het scenario in alle drivers waarvoor het geldt.
7. **Commit.**
   ⛔ Vraag toestemming om te committen en te pushen, met de prefix uit het profiel. Commit het scenario, de tests, de code en de eventuele ADR samen. Het commitbericht krijgt de kopjes **Rood gezien** (melding en reden), **Sabotage** (als je die deed) en **Niet geverifieerd**. Zo hoort het bewijs bij precies die versie. Schrijf geen apart controle-document voor de mens; wat de mens nog controleert, staat in het profiel of de werkwijze.
   Volg na de push de pipeline tot het einde. Is hij rood, dan gaat dat voor alles: zoek de oorzaak in de joblog en los die eerst op.
8. **Rapport.** Gebruik het format in §5.

Komt er werk bij dat niet bij dit voorbeeld hoort? Zet het als `@wip`-scenario of als backlogpunt neer. Neem het niet mee in deze iteratie.

## 2. Harde regels

- **Verander een goedgekeurd scenario of zijn verwachte waarden nooit zonder ⛔.** Dat geldt ook voor beweringen in de DSL. De mens keurt het voorbeeld goed; een AI die het voorbeeld aanpast om groen te worden, bewijst niets.
- Werk aan **één falend acceptatiescenario tegelijk**. Scenario's voor later krijgen `@wip` en tellen in de pipeline niet mee.
- Schrijf **geen productiecode zonder falende test.** Laat elke test eerst rood zien.
- **Verander een test nooit in dezelfde stap als de code die hij test.** Een test die meebeweegt met de code, bewijst niets.
- Schrijf de **kleinste implementatie** die de test groen maakt. Generaliseer pas als een volgende test daarom vraagt.
- **Refactor alleen op groen**, en altijd als aparte stap.
- **Ontdek je een regel die niet in het scenario staat?** Dan krijgt die een extra scenario of een expliciete unittest. Er mag nooit ongetest gedrag ontstaan.
- **Test wat je net gebouwd hebt.** Installeer eerst en controleer de versie. Een acceptatietest tegen een oude installatie zegt niets.
- **Rooktest ≠ acceptatietest.** Een status 200 bewijst niet dat het gedrag klopt.
- **Eén beslissing per ADR.** Een ADR herschrijf je nooit; je vervangt hem. Hij gaat in dezelfde commit als de code. Elke ADR noemt zijn Bewaking.
- **Infrastructuur is code.** Wijzig de omgeving alleen via de chart, de scripts of de pipeline. Gebruik nooit handmatige `kubectl edit`/`apply` of iets vergelijkbaars. Destructieve tests draaien alleen in wegwerpomgevingen. Geheimen horen niet in Git.
- **Blijf binnen de stack van het profiel.** Iets nieuws vraagt eerst om een ADR.
- **De pipeline is de scheidsrechter.** "Het werkt" geldt pas als de tests het laten zien, lokaal én in CI. Meld eerlijk wat niet geverifieerd is.

## 3. Het vierlaagse acceptatietestmodel

| Laag | Wat erin staat | Wat er níet in mag |
| --- | --- | --- |
| 1. Testcases | Het voorbeeld in domeintaal: Gegeven, Als, Dan | URL's, selectors, JSON, SQL |
| 2. DSL | Domeinmethodes (`klant("Jan").betaalt(...)`), aliassen voor testdata, beweringen | Hoe het systeem wordt aangestuurd |
| 3. Protocol drivers | HTTP-calls, page objects, shell/CLI. Eén interface, meerdere implementaties | Beweringen over gedrag |
| 4. Systeem | De echte, geïnstalleerde applicatie | Test-only endpoints of testcode |

- **Eén scenario, meerdere drivers.** Hetzelfde scenario draait via de API en via de UI. Scenario's die alleen over de UI gaan, krijgen een tag zoals `@ui`.
- **Isolatie via aliassen.** Elk scenario maakt zijn eigen data. De DSL vertaalt "Jan" naar een unieke naam per testrun. Zo hoef je geen gedeelde seed-data te gebruiken.
- **Gedeelde toestand,** zoals een globale klok of configuratie, kun je niet met aliassen isoleren. Geef zulke scenario's een tag en draai ze serieel. Leg die afweging vast in een ADR.
- **Testdata opzetten** gaat via één expliciet mechanisme, vastgelegd in een ADR. Gebruik een echt pad door het systeem of een seed-tool met de domeinregels, nooit een test-only endpoint.
- **Drivers spreken het systeem aan zoals een gebruiker**, via de publieke ingang. Ze gaan niet om beveiliging of routering heen.

Zie voor de uitwerking in een stack bijvoorbeeld [references/stack-playwright-ts.md](references/stack-playwright-ts.md).

## 4. Werken met AI (jij)

- Neem per stap **één falend scenario** als opdracht, met de relevante ADR's en de bestaande code. Lees niet het hele functionele ontwerp in om alles tegelijk te bouwen.
- **Stel voor, besluit niet.** Scenario's, verwachte waarden en ADR-beslissingen zijn van de gebruiker.
- **Laat elke rode test zien**, met de foutmelding, vóór je de code schrijft. Dan kan de gebruiker volgen dat de test echt iets bewijst.
- Refactoren is een **aparte stap**, nooit verstopt in een groen-stap.
- **Mutation testing** (PIT, Stryker) is de geautomatiseerde sabotage. Draai het commando uit het profiel vóór de commit, en noem levende mutanten in het rapport. Een levende mutant in nieuwe code dood je met een test. Een gelijkwaardige mutant leg je uit. Staat mutation testing nog niet in de pipeline? Stel dan een ADR voor: met de hand saboteren is eenmalig.
- **Valkuilen die je oplost en die niet uit de code af te leiden zijn**, stel je voor als aanvulling:
  - een projectvalkuil gaat naar het profiel;
  - een valkuil van een tool gaat naar de stack-referentie.

## 5. Definition of Done en rapport

Een slice of feature is klaar als:
- de scenario's groen zijn in de acceptance stage van de pipeline, in elke relevante driver;
- alle code test-first is ontstaan en de unittests groen zijn;
- elke wijziging aan infrastructuur een template- of scripttest heeft, en bij gedrag in de omgeving ook een installatiescenario;
- nieuwe fitness functions groen zijn;
- elke architectuurbeslissing een ADR met Bewaking heeft;
- er geen `@wip`-scenario's van deze slice meer zijn;
- de specs de actuele beschrijving van het gedrag zijn.

**Rapport na elke iteratie** (kort, in deze volgorde):

1. **Scenario:** de naam, en in welke drivers het draait.
2. **Rood gezien:** per test de melding, en waarom dat de goede reden was.
3. **Groen:** wat lokaal groen is, of de pipeline groen is (met run-id of link), en de mutatiescore met de levende mutanten.
4. **Niet geverifieerd:** wat je niet hebt kunnen controleren, en waarom.
5. **ADR's:** nieuw, gewijzigd of voorgesteld.
6. **Uitleg:** vijf tot tien regels voor de developer die hier straks verder bouwt:
   - **Route:** hoe het scenario door de lagen loopt, met `bestand:regel` per stap;
   - **Ontwerpkeuze:** waarom de code zo is opgebouwd, met de ADR als die er is;
   - **Kritische punten:** waar een volgende wijziging het makkelijkst misgaat, en wat de tests níet afdekken.
   Schrijf alleen op wat je in de code hebt gezien. Voor meer diepgang is er de skill `uitleg`.
7. **Volgende stap:** het volgende voorbeeld uit de backlog, of wat er open staat.
