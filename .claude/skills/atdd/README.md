# Skill `atdd`: acceptance test-driven development volgens Farley

Deze map is een [Claude Code-skill](https://docs.claude.com/en/docs/claude-code/skills). Het is een werkinstructie die Claude laadt zodra je aan een project werkt dat volgens *Modern Software Engineering* van Dave Farley wordt gebouwd. De werkwijze heeft vier pijlers:

- double-loop outside-in TDD;
- het vierlaagse acceptatietestmodel;
- ADR's met bewaking;
- geautomatiseerde kwaliteitsvangrails (fitness functions en anti-AI-slop-regels);
- de deployment pipeline als scheidsrechter.

De skill is ontstaan in BankSim-MSE. Hij is zo opgezet dat je hem ongewijzigd in andere projecten kunt gebruiken.

## Waarom een skill

Zonder skill begint elke sessie met Claude blanco. Dat heeft drie gevolgen:

- **Claude volgt zijn eigen gewoontes.** Die botsen precies met ATDD: test en code in één keer schrijven, een feature in één grote stap afmaken, en melden dat "het werkt" op basis van een eigen inschatting.
- **De afspraken moet je steeds herhalen.** Je moet telkens zeggen dat er per stap één scenario is, dat jij scenario's en ADR's goedkeurt, en dat er niet zonder toestemming gecommit wordt.
- **Opgeloste problemen keren terug.** Problemen die niet uit de code af te leiden zijn, worden opnieuw ontdekt. Denk aan bestandsrechten in CI, de timing van een vers cluster, of een acceptatietest tegen een oude installatie.

De skill geeft:

| | Wat je krijgt |
| --- | --- |
| **Vaste procedure** | Per iteratie: voorbeeld → scenario → rood om de goede reden → outside-in de binnenste lus → lokaal verifiëren → commit → pipeline → rapport |
| **Stoppunten** | Claude stopt bij drie momenten: (1) het scenario en de verwachte waarden, (2) een architectuurbeslissing, (3) commit en push. Jij blijft eigenaar van de voorbeelden en de beslissingen |
| **Harde regels** | Eén falend scenario tegelijk, geen code zonder rode test, test en code nooit in dezelfde stap, refactoren alleen op groen, nieuwe tools alleen met een ADR |
| **Bewaarde kennis** | Valkuilen en sjablonen per stack, ontleend aan code die echt gedraaid heeft |
| **Controleerbaar rapport** | Na elke iteratie: welke tests rood zijn gezien en waarom, wat groen is (lokaal en in CI) en wat níet is geverifieerd. Plus een korte uitleg: de route door de code, de ontwerpkeuze en de kritische punten |

### Wat de skill níet doet

- **Hij dwingt niets af.** Een skill is een instructie. Hij maakt het juiste gedrag veel waarschijnlijker, maar garandeert het niet. Wat echt telt, is de CI-pipeline van het project. Wil je harde afdwinging, gebruik dan [hooks](https://docs.claude.com/en/docs/claude-code/hooks) of permissies. Een hook kan bijvoorbeeld `git commit` blokkeren zonder bevestiging.
- **Hij vervangt de projectdocumentatie niet.** Waarom het project zo werkt, wat er gebouwd moet worden en welke beslissingen er al liggen, staat in het project zelf. Het profiel verwijst ernaar.

## Skill en profiel

De skill is algemeen. Alles wat per project verschilt, staat in een kort **ATDD-profiel** in het project. Meestal is dat een sectie `## ATDD-profiel` in `CLAUDE.md`, omdat Claude Code dat bestand in elke sessie automatisch laadt.

| In de skill (algemeen) | In het profiel (per project) |
| --- | --- |
| De iteratie, de stoppunten, de harde regels | De stack en de toegestane tools |
| Hoe vangrails werken en de anti-AI-slop-regels | Welke vangrails gelden, en met welke test |
| Het vierlaagse model | De commando's: commit stage, installeren + accepteren, één scenario, de pipeline volgen |
| Het ADR-sjabloon en de ADR-regels | Paden: specs, DSL, drivers, unittests, infra-tests, ADR's |
| Walking skeleton (stap 0) | Doelomgeving en wat daar verboden is |
| Definition of Done en het rapport | Afspraken: commitprefix, toestemming, lokale tools |
| Sjablonen en valkuilen per stack (`references/stack-*.md`) | Projectvalkuilen en waar de backlog staat |

Voorbeeld: [`CLAUDE.md` van BankSim-MSE](../../../CLAUDE.md).

## Inhoud van de map

```text
atdd/
├── SKILL.md                     de instructie die Claude laadt (procedure, regels, model, vangrails, DoD)
├── README.md                    dit document
└── references/                  alleen geladen als het nodig is
    ├── profiel-sjabloon.md      velden van een profiel, met een ingevuld voorbeeld
    ├── walking-skeleton.md      stap 0 test-first, en de pipeline één keer rood zien
    ├── adr-sjabloon.md          het sjabloon van Nygard met Bewaking, en de regels
    ├── stack-playwright-ts.md   acceptatietests: vier lagen in Playwright Test, REST- en UI-driver
    ├── stack-java-spring.md     outside-in met JUnit/MockMvc, fitness functions
    ├── stack-angular-vitest.md  componenttests met HttpTestingController
    └── stack-helm-bats.md       Helm, bats, kind en CI: tests, patronen en echte valkuilen
```

## Gebruik

### Expliciet aanroepen

Typ `/atdd`, eventueel met een opdracht:

```text
/atdd begin aan slice 1
/atdd volgend scenario
/atdd voeg een scenario toe: een betaling zonder omschrijving wordt geweigerd
/atdd de pipeline is rood
/atdd stel een ADR op voor keyset-paginering
/atdd nieuw project
```

### Automatisch

Je hoeft de skill niet te noemen. Bij verzoeken als "begin aan de volgende feature", "voeg een acceptatietest toe" of "de pipeline faalt" laadt Claude hem zelf, op basis van de beschrijving in `SKILL.md`.

### Verloop van één iteratie

1. Claude leest het profiel en bepaalt de stand van zaken uit de repo: specs, `@wip`-tags, ADR's en de README.
2. Claude stelt één scenario voor, met concrete waarden. ⛔ **Jij keurt het goed of past het aan.**
3. Claude draait het scenario en laat zien dat het faalt, en waarom dat de goede reden is.
4. Claude werkt de binnenste lus outside-in af. Bij elke stap zie je eerst de rode unittest met de melding, dan de kleinste code, en daarna eventueel een aparte refactorstap.
5. Vraagt het werk om een architectuurkeuze? Dan schrijft Claude een ADR-concept. ⛔ **Jij beslist.**
6. Claude verifieert lokaal: eerst de commit stage, dan installeren en de acceptatietests.
7. ⛔ **Claude vraagt toestemming om te committen**, pusht daarna en volgt de pipeline.
8. Je krijgt het rapport.

Lees vooral punt 2 ("rood gezien") en punt 4 ("niet geverifieerd") van het rapport. Daar zie je of de tests echt iets bewijzen.

## In andere projecten gebruiken

Er zijn drie manieren om de skill in een ander project te krijgen.

| Manier | Hoe | Voordeel | Nadeel |
| --- | --- | --- | --- |
| **Kopiëren naar het project** | `cp -R <banksim-mse>/.claude/skills/atdd <ander-project>/.claude/skills/` | Gaat mee in de Git van dat project, ook voor teamgenoten | Verbeteringen moet je met de hand overzetten |
| **Symlink in je gebruikersmap** | `ln -s <banksim-mse>/.claude/skills/atdd ~/.claude/skills/atdd` | Beschikbaar in al je projecten; verbeteringen werken overal direct door | Alleen voor jou; het project zelf bevat de skill niet |
| **Eigen repo of plugin** (later) | De map naar een eigen repository verplaatsen, of als Claude Code-plugin publiceren | Eén bron, met versies, deelbaar | Pas zinvol als de skill stabiel is |

Gebruik niet de symlink én een kopie tegelijk. Je hebt dan twee skills met dezelfde naam, en het is niet duidelijk welke Claude Code laadt.

### Een nieuw project starten

1. **Maak de skill beschikbaar,** via een kopie of de symlink.
2. **Stel het profiel op.** Start Claude Code in het project en typ `/atdd nieuw project`. Claude leest de repo en vult in wat hij kan zien. Wat hij niet kan zien, vraagt hij: de doelomgeving, de afspraken en de backlog. Daarna schrijft hij een sectie `## ATDD-profiel` in `CLAUDE.md`. Het sjabloon staat in `references/profiel-sjabloon.md`.
3. **Bouw het walking skeleton,** als er nog geen pipeline of acceptatietest is. Dat is één scenario dat van begin tot eind werkt, met een pipeline die je één keer rood en daarna groen hebt gezien. Zie `references/walking-skeleton.md`.
4. **Schrijf het eerste echte scenario** uit de backlog, met `/atdd volgend scenario`.

### Een bestaand project overzetten

Hiervoor heb je geen herschrijving nodig.
1. Stel het profiel op.
2. Leg de bestaande pipeline en tests vast als vertrekpunt.
3. Voeg vanaf nu elke wijziging toe als nieuw scenario.

Bestaande end-to-end-tests kun je stap voor stap naar de vier lagen ombouwen, zodra je ze toch aanraakt.

### Een andere stack

De procedure, de regels en het model in `SKILL.md` zijn stack-onafhankelijk. Voor een stack zonder `references/stack-*.md` werkt de skill ook. Er zijn dan alleen geen kant-en-klare sjablonen.

Een nieuwe stack voeg je zo toe:
1. Bouw eerst het walking skeleton.
2. Schrijf daarna `references/stack-<naam>.md` met werkende voorbeelden uit dat project, en de valkuilen die je echt bent tegengekomen.
3. Noem het bestand in de lijst hierboven.

## Onderhoud

- **Een nieuwe valkuil gaat eerst naar het profiel van het project.** Treedt hij in een tweede project ook op, of hoort hij bij een tool, verplaats hem dan naar de juiste `references/stack-*.md`.
- **Houd `SKILL.md` kort,** onder ongeveer 250 regels. Details horen in `references/`; die worden alleen geladen als ze nodig zijn.
- **Sjablonen komen uit code die echt gedraaid heeft.** Verandert de stack, werk dan het sjabloon bij vanuit het project.
- **Geen projectstatus of datums in de skill.** Die leest Claude uit de repo.
