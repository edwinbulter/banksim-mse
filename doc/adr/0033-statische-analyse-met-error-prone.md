# 0033. Statische analyse van Java met Error Prone

Datum: 2026-11-01
Status: Geaccepteerd

## Context
De Java-code heeft de compiler, ArchUnit (de vangrails) en PIT, maar geen statische analyse. Fouten die de compiler niet ziet en die geen test toevallig raakt, blijven liggen: een `toUpperCase()` zonder `Locale`, een genegeerde `Future`, een impliciete tekenset. Farley: vertrouw op geautomatiseerde controles vóór productie, niet op mensen die code lezen (backlog fase 6.3).

**Spike op 2026-11-01**, Error Prone 2.50.0 op JDK 21 met de Maven-wrapper:
- **Werkt**, met tien `--add-exports`/`--add-opens` in `backend/.mvn/jvm.config` en `-XDaddTypeAnnotationsToSymbol=true`.
- **Duur:** `clean test-compile` van alle vier de modules 9 seconden in plaats van 4 à 5.
- **Bevindingen op de bestaande code:** 0 fouten en 9 waarschuwingen:
  - `StringCaseLocaleUsage` in `Zoekfilter`: `toUpperCase()` zonder `Locale`. In een Turkse locale wordt een "i" een "İ", en dan vindt het zoeken op IBAN niets. Een echte fout.
  - `CharacterGetNumericValue` in `Iban`: `Character.digit` is wat bedoeld wordt.
  - `DefaultCharset` en `FutureReturnValueIgnored` in tests.
  - `NotJavadoc` en `MissingSummary` (5×): stijl van de Javadoc.

Opties:
1. **Error Prone**, als plugin van de compiler. Draait bij elke build, dus in de commit stage en lokaal, zonder aparte stap. Weinig valse meldingen; een fout is een compileerfout.
2. **SpotBugs**, op de bytecode. Een aparte stap, trager, met meer valse meldingen.
3. **Checkstyle**, vooral stijl. Vangt weinig echte fouten.

## Beslissing
We kiezen optie 1, Error Prone.

**Welke controles.** Error Prone heeft honderden controles, elk met een niveau:
- **ERROR**: een vrijwel zekere fout, zoals `equals` tussen types die nooit gelijk kunnen zijn;
- **WARNING**: een waarschijnlijke fout of een valkuil, zoals `toUpperCase()` zonder `Locale`;
- **uit**: experimentele of omstreden controles, die je zelf moet aanzetten.

We gebruiken de controles die standaard aan staan, dus ERROR en WARNING, zoals Error Prone ze levert. We zetten geen extra controles aan en geen controles uit.

**Wat er gebeurt bij een melding.** Elke melding, ERROR of WARNING, breekt de build: naast de ERROR-controles zet `failOnWarning` in de `maven-compiler-plugin` ook elke waarschuwing om in een fout. Dat geldt ook voor de waarschuwingen van javac zelf.
- Het compileren stopt. De melding noemt het bestand, de regel, de naam van de controle (zoals `[StringCaseLocaleUsage]`), de uitleg en meestal een voorgestelde oplossing.
- Lokaal faalt `make backend-test`, en dus `make commit-stage`.
- In CI faalt de job `backend`, en daarmee de pipeline. De jobs die ook compileren, zoals `images` en "Mutatietests (PIT)", falen ook.

**Wat je dan doet.**
1. **De code verbeteren.** Dat is de gewone weg, net als bij een rode test.
2. **Is de melding onterecht** (een valse melding), dan mag één plek een uitzondering krijgen: `@SuppressWarnings("NaamVanDeControle")` op de kleinste plek, met een commentaar waarom. Dat is een vangrail verzwakken, dus het vraagt ⛔ en een regel in deze ADR.
3. **Een controle helemaal uitzetten** (`-Xep:NaamVanDeControle:OFF`) vraagt ⛔ en een nieuwe ADR die deze vervangt.

Zo blijft de lijst met meldingen altijd leeg, en valt een nieuwe melding op in plaats van te verdwijnen tussen oude.

**De bestaande meldingen.** De negen waarschuwingen uit de spike worden opgelost vóórdat `failOnWarning` aan gaat. De fout in `Zoekfilter` krijgt eerst een test die rood is, met een Turkse locale.

## Gevolgen
- Compileren duurt ongeveer 5 seconden langer, lokaal en in elke job die compileert.
- `backend/.mvn/jvm.config` is nodig; zonder die opties start Error Prone niet op JDK 21. IntelliJ leest de compiler-opties uit de pom, maar niet uit `jvm.config`: draai bij twijfel `make backend-test`.
- Een upgrade van Error Prone kan nieuwe waarschuwingen geven en dus de build breken. Dat is de bedoeling: de upgrade lost ze in dezelfde commit op.
- Error Prone ziet geen architectuur; dat blijft ArchUnit. Het vervangt ook geen tests; dat blijft PIT.

## Bewaking
- **De build zelf:** `make backend-test` en de jobs `backend` en "Mutatietests (PIT)" compileren met Error Prone en `failOnWarning`.
- **Eén keer rood gezien:** een `toLowerCase()` zonder `Locale` in een productieklasse breekt de build.
- In het profiel (`CLAUDE.md`) als vangrail.
