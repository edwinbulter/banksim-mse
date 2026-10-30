# Sjabloon: ATDD-profiel van een project

Het profiel bevat alles wat per project verschilt. Zet het als sectie `## ATDD-profiel` in `CLAUDE.md` in de root van het project. Dan wordt het in elke sessie geladen. Een apart bestand `doc/atdd-profiel.md` kan ook.

Houd het kort: 40 tot 60 regels. Achtergrond en backlog horen in de projectdocumentatie. Het profiel verwijst ernaar.

## Velden

| Veld | Vraag aan de gebruiker | Voorbeeld (BankSim-MSE) |
| --- | --- | --- |
| Stack | Welke talen, frameworks en testtools? Mag er iets bij zonder ADR? | Java 21/Spring Boot, Angular/Vitest, Playwright TS, Helm, kind. Nieuwe tools alleen met een ADR |
| Commit stage | Welk commando draait alle snelle tests? | `make commit-stage` |
| Installeren + accepteren | Hoe installeer je de nieuwe versie en draai je de acceptatietests? | `make acceptance` (`install` + `e2e` + `e2e-infra`) |
| Mutation testing | Welk commando muteert de code en faalt onder een drempel? Draait het in de pipeline? | `make mutatie` (PIT, ADR 0018), job "Commit stage – mutatietests" |
| Eén scenario | Hoe draai je één scenario in één driver? | `cd e2e && npx playwright test --project=rest specs/skelet.spec.ts` |
| Versie controleren | Waar zie je welke build er draait? | De voettekst en `GET /api/versie` tonen de image-tag |
| Pipeline | Waar draait CI, en hoe volg je een run? | GitHub Actions: `gh run list`, `gh run watch <id>` |
| Paden | Waar staan de specs, DSL, drivers, unittests, infra-tests en ADR's? | `e2e/specs`, `e2e/dsl`, `e2e/drivers/{rest,ui,shell}`, `doc/adr` |
| Doelomgeving | Waar wordt geïnstalleerd, en wat mag daar níet? | Alleen `kind-single-node`, namespace `banksim-mse`. Destructieve tests alleen in CI |
| Afspraken | Commitprefix, toestemming, lokale tools | `[ECS-63]`; nooit committen zonder toestemming; Node 24 via PATH |
| Vangrails | Wat moet automatisch afgedwongen worden, en welk ontwerp is verboden? | Domein zonder Spring (ArchUnit); DTO's blijven in `api`; geen `synchronized` in service of controller; geen `LocalDate.now()` buiten de klok |
| Projectvalkuilen | Wat ging eerder mis en is niet uit de code af te leiden? | `make e2e` test wat er geïnstalleerd is, dus gebruik `make acceptance` |
| Backlog | Waar staan de volgende features of voorbeelden? | `doc/werkwijze-mse.md`, hoofdstuk 7 |

## Leeg sjabloon

```markdown
## ATDD-profiel

Werkwijze: skill `atdd` (`.claude/skills/atdd/`). Dit profiel bevat wat specifiek is voor dit project.

**Stack:** …  Nieuwe libraries of tools alleen met een ADR.

**Commando's**
- Commit stage: `…`
- Installeren + acceptatietests: `…`
- Eén scenario: `…`
- Welke versie draait: `…`
- Pipeline volgen: `…`

**Waar komt wat**
| Wijziging | Waar | Test |
| --- | --- | --- |
| Scenario | … | … |
| DSL / drivers | … | via scenario's |
| Domein / API / UI | … | … |
| Infrastructuur | … | … |
| ADR | … | – |

**Vangrails** (SKILL.md §4; elk één keer rood gezien)
| Vangrail | Test | Waar |
| --- | --- | --- |
| Laagindeling, domein zonder framework | … | … |
| … | … | … |

**Doelomgeving:** …  Verboden: …

**Afspraken:** commitprefix …; nooit committen of pushen zonder toestemming; …

**Valkuilen:** …

**Backlog en achtergrond:** …
```

## Opstellen met de gebruiker

1. Lees eerst de repo: de build-bestanden, CI-workflows, testmappen, README en de bestaande ADR's. Vul in wat je zeker weet.
2. Vraag alleen naar wat je niet kunt zien: de doelomgeving, afspraken, de backlog en wat verboden is.
3. Controleer elk commando door het te draaien, of markeer het als "nog niet geverifieerd".
4. Ontbreken de pipeline of de acceptatietests nog? Zet dan in het profiel: "Stap 0 (walking skeleton) nog niet af". Begin daarna met [walking-skeleton.md](walking-skeleton.md).
