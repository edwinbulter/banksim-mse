# 0018. Mutation testing met PIT in de commit stage

Datum: 2026-10-23
Status: Geaccepteerd

## Context
Elke test is één keer rood gezien, en tijdens het bouwen is soms met de hand gesaboteerd: een regel weghalen en zien dat een test faalt. Dat bewijs is eenmalig. Wordt een test later zwakker, of komt er code bij die geen test echt controleert, dan merkt niemand het. De controle-documenten per slice lieten de gebruiker die sabotages met de hand herhalen. Dat kostte 45 tot 90 minuten per slice, en het document verouderde. Werkwijze §10 en de skill `atdd` noemen mutation testing al, maar het draait nergens.

Mutation testing herhaalt die sabotage automatisch. De tool verandert de code op honderden plekken: `<` wordt `<=`, een conditie wordt omgekeerd, een aanroep verdwijnt. Per verandering ("mutant") draait hij de tests. Een mutant die blijft leven, is een plek waar de tests niets controleren.

**Spike op 2026-10-23,** in een kopie van de backend, met `pitest-maven` 1.30.1 en `pitest-junit5-plugin` 1.2.3:
- **Werkt met JUnit 6** (Spring Boot 4.0.8), ook met de Testcontainers-tests van `bank-api` en `bank-seed`.
- **Duur:** 64 seconden lokaal, voor alle vier de modules.
- **Scores (gedood/totaal):**
  - `bank-domain` 16/16;
  - `bank-api` 43/49;
  - `bank-bff` 9/9;
  - `bank-seed` 36/47.
- **Echte gaten** (drie levende mutanten):
  - `Cursor`: een sleutel van precies 32 tekens. De grens `< 32` wordt niet getest.
  - `SeedCommando.opties` en `SeedCommando.transacties`: een optie zonder waarde aan het eind (`i + 1 < args.length`).
- **Spring-configuratie is niet te meten.** In `BeveiligingConfig` overleeft het weghalen van `setJwtGrantedAuthoritiesConverter`, ook als enige mutant. Toch vangt `TokenControleTests` dezelfde fout wél als je hem met de hand maakt (`SCOPE_` in plaats van `ROLE_`). Die code draait alleen bij het opbouwen van de Spring-context. PIT kent haar daardoor waarschijnlijk toe aan één test, en draait de mutant alleen daartegen. Hetzelfde geldt voor `KlokConfig` en `BankSeedApplication`. Die laatste wordt door geen test gestart.

Opties:
1. **PIT als eigen job in de commit stage**, parallel aan `backend`, met een drempel. Dan is elke commit gecontroleerd. Het kost een job van een paar minuten.
2. **PIT alleen 's nachts.** Commits worden niet trager, maar een zwakke test blijft een dag onopgemerkt, en de nachtelijke run is van niemand.
3. **PIT alleen lokaal** (`make mutatie`). Dit is goedkoop, maar het herhaalt zich niet vanzelf. Het is precies het probleem van de controle-documenten.

## Beslissing
- **Tool.** PIT (`pitest-maven` met `pitest-junit5-plugin`). De versies staan als properties in `backend/pom.xml`.
- **Waar.** Een eigen job "Commit stage – mutatietests" in de pipeline, parallel aan `backend`. Lokaal draait hetzelfde met `make mutatie`. Het HTML-rapport komt als artifact mee.
- **Wat.** Alle modules, met de bestaande tests, ook die met Testcontainers.
- **Uitgesloten.** Klassen die alleen Spring-configuratie of een `main` zijn: `*Config` en `*Application`. PIT meet ze niet betrouwbaar (zie Context). Ze blijven gedekt door hun eigen tests, zoals `TokenControleTests` en `BffTests`, en door de acceptatietests.
- **Drempel.** Eerst worden de drie echte gaten gedicht, met een test die de mutant doodt. Daarna geldt per module een `mutationThreshold` van 95%. Een nieuwe levende mutant in het rapport is een signaal: schrijf een test, of leg in een commentaar uit waarom de mutant gelijkwaardig is.
- **Grenzen.** PIT muteert Java-bytecode. Hij muteert geen SQL-strings, YAML, Helm-templates of Angular. Een filter als `datum <= :vandaag` in een SQL-string blijft dus gedekt door eerst rood, de repositorytests met Testcontainers en de acceptatietests. Stryker voor de frontend is een aparte beslissing (backlog in werkwijze §6).

## Gevolgen
- Het met de hand herhalen van sabotages vervalt. De controle-documenten per slice worden gearchiveerd (werkwijze §10.1).
- Elke commit krijgt een job van naar schatting 2 tot 4 minuten erbij, parallel aan de andere commit-stagejobs. De acceptance stage wacht er niet langer door, want die wacht al op `images`.
- Een nieuwe klasse zonder sterke tests laat de pipeline falen. Dat is de bedoeling, maar het kan voelen als extra werk bij een refactorstap.
- Er komt een Maven-plugin bij, met een versie die bij JUnit moet passen. Bij een upgrade van Spring Boot of JUnit draait de spike opnieuw mee: de job faalt dan, of hij vindt geen tests.
- **De drempel vangt geen enkele zwakke test.** Bij de invoering haalden alle modules 100%. Een module heeft 6 tot 41 mutanten. 95% laat dus één of twee levende mutanten per module door. Zo bleef het weghalen van één test (de grens van de cursorsleutel) groen: `bank-api` 39/40, 98%. De drempel vangt een grote achteruitgang. Een enkele levende mutant zie je alleen in het rapport. Daarom staat het bekijken van de levende mutanten bij de controle door de mens (werkwijze §10.1). De keuze voor 100% is overwogen en afgewezen: een gelijkwaardige mutant zou dan de pipeline blokkeren.
- Klassen met Spring-configuratie vallen buiten de meting. Komt er logica in zo'n klasse, dan hoort die in een eigen klasse die wel gemeten wordt. Een voorbeeld is de omzetting van `realm_access.roles` in `realmRollen()`.

## Bewaking
- De job "Commit stage – mutatietests" faalt onder de drempel. Bewezen bij de invoering: zonder `CursorTests` faalt `make mutatie` met "Mutation score of 93 is below threshold of 95" (`bank-api`).
- Vindt PIT geen mutaties, bijvoorbeeld door een verkeerd filter, dan faalt de job ("No mutations found"). Ontbreekt de JUnit-plugin, dan faalt hij ook ("Please check you have correctly installed the plugin"). Allebei gezien bij de invoering. De job wordt dus niet stilletjes groen.
- Bij de invoering vond PIT meteen een vierde gat, in de nieuwe controle op een losse optie in `SeedCommando`: een aanroep zonder argumenten werd niet getest.
