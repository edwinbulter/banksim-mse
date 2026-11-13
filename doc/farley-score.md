# Farley Score van de tests

Dit document laat zien hoe de Farley Score-plugin de tests van BankSim-MSE beoordeelt, en bespreekt of dat een goed resultaat is. De meting is van 13 november 2026, op commit `0d723d1`.

## Wat de Farley Score-plugin is

De [Farley Score-plugin](https://github.com/cd-training-courses/farley_score_plugin) is een plugin voor Claude Code. Hij meet de *kwaliteit* van tests, niet de dekking. Je start hem met `/msec:farley-score`. Daarnaast is er `/msec:farley-score-coach`, die je met vragen en oefeningen leert tests te beoordelen.

De plugin beoordeelt de tests op de acht eigenschappen van goede tests van Dave Farley ([Properties of Good Tests](https://www.linkedin.com/pulse/tdd-properties-good-tests-dave-farley-iexge/)):

| Code | Eigenschap | Gewicht | Wat hij meet |
| --- | --- | --- | --- |
| U | Understandable | 1,50x | Een test leest als een specificatie |
| M | Maintainable | 1,50x | Een test controleert gedrag, niet de implementatie, en overleeft een refactor |
| R | Repeatable | 1,25x | Elke keer en overal dezelfde uitkomst |
| A | Atomic | 1,00x | Geïsoleerd, zonder gedeelde toestand |
| N | Necessary | 1,00x | Elke test voegt iets toe |
| G | Granular | 1,00x | Eén uitkomst per test, zodat een rode test precies aanwijst wat fout is |
| F | Fast | 0,75x | Rekenwerk, geen I/O |
| T | First (TDD) | 1,00x | De test is vóór de code geschreven |

Hij werkt in vier stappen:

1. **Inventaris.** Hij zoekt de testbestanden en herkent de taal en de frameworks. Bij meer dan 50 testbestanden neemt hij een vaste steekproef van 30%, gekozen op de SHA-256 van het pad. Dezelfde code geeft dus dezelfde steekproef.
2. **Signalen.** Per testmethode zoekt hij signalen. Een negatief signaal is bijvoorbeeld `Thread.sleep`, gedeelde toestand of een naam als `test1`. Een positief signaal is bijvoorbeeld een naam die gedrag beschrijft of een geparametriseerde test. Hij zoekt ook naar *tautology theatre*: tests die ook groen blijven als je alle productiecode weggooit, zoals een mock die teruggeeft wat de test erin stopte.
3. **Score.** Per eigenschap telt een statische score (uit de signalen) voor 60% en het oordeel van het taalmodel voor 40%. Een Python-calculator in de plugin rekent de gewogen Farley Index uit: `(U·1,5 + M·1,5 + R·1,25 + A + N + G + F·0,75 + T) / 9`.
4. **Rapport.** De score per eigenschap met bewijs, de vijf zwakste tests en aanbevelingen op volgorde van effect.

De schaal:

| Farley Index | Oordeel |
| --- | --- |
| 9,0 – 10,0 | Exemplary |
| 7,5 – 8,9 | Excellent |
| 6,0 – 7,4 | Good |
| 4,5 – 5,9 | Fair |
| 3,0 – 4,4 | Poor |
| 0,0 – 2,9 | Critical |

De plugin leest alleen; hij verandert geen code. De scoremethode komt van Andrea Laforgia ([test-design-reviewer](https://github.com/andlaf-ak/claude-code-agents/tree/main/test-design-reviewer)).

Let op: de helft van de score is een oordeel van een taalmodel, en bij meer dan 50 bestanden ziet de plugin maar 30% van de tests. Een tweede meting kan dus een paar tienden anders uitvallen. Gebruik de index als richting, niet als exacte maat.

## Het rapport

Hieronder staat het rapport zoals de plugin het opleverde, met het hele project als doel. Bevindingen die achteraf niet helemaal klopten, staan in [Controle achteraf](#controle-achteraf).

### Farley Index: 7,46 / 10 (Good, net onder Excellent)

De grens naar Excellent ligt op 7,5.

#### Scores per eigenschap

| Eigenschap | Statisch | LLM | Samen | Gewicht | Gewogen | Belangrijkste bewijs |
|---|---|---|---|---|---|---|
| Understandable | 9,3 | 9,0 | **9,2** | 1,50x | 13,78 | Namen in domeintaal (`weigertEenCursorVanEenAndereRekening`); Javadoc met ADR/FO; Gegeven/Als/Dan in e2e |
| Maintainable | 8,0 | 7,5 | **7,8** | 1,50x | 11,73 | Fakes (`GeheugenGrootboek`) in plaats van mocks; DSL in e2e. Wel strikte Keycloak-verwachtingen en een vastgelegde aanroepvolgorde in `DemoDataTests` |
| Repeatable | 5,1 | 8,0 | **6,3** | 1,25x | 7,83 | Overal `Clock.fixed`, `Random(7)`. Maar `Thread.sleep(500)` in 2 tests, en 52 tests tegen een database |
| Atomic | 8,4 | 7,0 | **7,8** | 1,00x | 7,82 | Eén container per klasse; tests voegen zelf rijen toe aan de gedeelde DB; de simulatiedatum geldt voor de hele bank |
| Necessary | 4,1 | 8,5 | **5,9** | 1,00x | 5,87 | Geen tautologieën en niets uitgeschakeld; weinig parametrisering (de statische regel telt alleen dat als positief) |
| Granular | 8,2 | 7,0 | **7,7** | 1,00x | 7,73 | Meestal één uitkomst per test; een handvol mega-tests |
| Fast | 4,7 | 5,5 | **5,0** | 0,75x | 3,75 | 52 DB-tests, 26 Spring-contexttests, 21 e2e-tests tegen het cluster |
| First (TDD) | 8,8 | 8,5 | **8,7** | 1,00x | 8,67 | 89 van de 92 commits met productiecode bevatten ook tests; namen lezen als specificaties |

#### Signalen

| Signaal | Aantal | Raakt | Ernst |
|---|---|---|---|
| Database (Testcontainers) | 52 | R, F | Laag (bewust gekozen, deterministisch) |
| e2e tegen het geïnstalleerde systeem | 21 | R, F | Laag (acceptatielaag) |
| `Thread.sleep(500)` met daarna `isNotDone()` | 2 | R, F | Medium |
| Lokale HTTP-server (JWKS) | 6 | R, F | Laag |
| Tests die rijen toevoegen aan een gedeelde DB | 5 | A | Medium |
| Setup van meer dan 15 regels (`@BeforeAll`) | 4 | A | Laag |
| Strikte interactieverwachtingen (MockRestServiceServer, STRICT JSON) | 6 | M | Medium |
| Vastgelegde aanroepvolgorde en aantallen (`gedaan`, `hasSize(62)`) | 2 | M, U | Medium |
| Asserts op ruwe SQL-rijen in plaats van op de opslag-interface | 2 | M | Laag |
| Mega-tests (meer dan 5 asserts of meer dan 20 regels) | ~22 | G | Medium |
| Namen die meerdere gedragingen noemen ("...En...") | 6 | G | Laag |
| Magische aantallen in asserts | 4 | U | Laag |
| Patches voor coverage en regressie ("gevonden door PIT", "Backlog fase 7.x") | 5 | T | Laag |
| Uitgeschakelde tests, reflectie, `@Order` | 0 | – | – |

#### Tautology Theatre

- **Mock-tautologieën:** geen gevonden. Er is geen Mockito in de testcode. Het enige `@MockitoBean` is de `JwtDecoder`, die alleen nodig is om de context te laten starten.
- **Tests met alleen mocks:** geen gevonden. Ook de tests met fakes (`DemoDataTests`, `NachtelijkeAfsluitingTests`) draaien echte productiecode.
- **Triviale tautologieën:** geen gevonden.
- **Frameworktests:** geen gevonden. `TokenControleTests` gebruikt een testcontroller, maar test de eigen `BeveiligingConfig` en niet Spring zelf.

**0** gevallen in **0** van de **211** testmethoden.

#### De 5 zwakste tests (geschat per methode)

1. **`JdbcTransactieRepositoryTests.geeftDeDetailsVanEenTransactie`**, ~5,5. Tien asserts over drie transacties en twee slices (S4.1 en S7.1). De test pakt transacties op index (`get(2)`). Bij een rode test weet je niet welk detail fout is.
2. **`DemoDataTests.maaktTienKlantenMetEenBetaalEnSpaarrekeningEnLoginEnDaarnaDeBeheerder`**, ~5,5. `hasSize(62)`, `" 133"` en `" 63"` zijn niet verklaard. `subList(0, 6).containsExactly(...)` legt de volgorde van de aanroepen vast. Een refactor die het gedrag niet verandert, kan deze test toch rood maken.
3. **`app.spec.ts` › "maakt voor elk nieuw controlescherm een nieuwe Idempotency-Key"**, ~6,0. Een lus van ruim 20 regels door de hele UI-flow, met de verwachtingen pas na de lus. Daarbij heeft de hele spec van 1072 regels geen `afterEach(() => http.verify())`, dus onverwachte extra verzoeken vallen nergens op.
4. **`RenteOpslagTests.tweeGelijktijdigeAfsluitingenMarkerenDeMaandEenKeer`** (en het tweelingtest in `JdbcIdempotentieTests`), ~6,0. Na `Thread.sleep(500)` volgt `isNotDone()`. Dit is waarschijnlijk niet flaky, maar kan vals groen zijn: start de tweede transactie op een trage machine pas na 500 ms, dan klopt `isNotDone()` ook als de lock niet werkt. Dat is niet gereproduceerd.
5. **`KeycloakLoginsTests.haaltEenTokenMetHetServiceAccountEnMaaktDeGebruikerMetWachtwoord`**, ~6,5. Ruim 25 regels strikte verwachtingen (URL, header, form en STRICT JSON), en de naam noemt twee gedragingen. Dat de verwachtingen het HTTP-contract met Keycloak vastleggen, is te verdedigen. Ze zijn wel broos.

#### Aanbevelingen (op volgorde van effect)

1. **Necessary en Granular: parametriseer de varianten.** De zoektests in `JdbcTransactieRepositoryTests` (`zoektOp*`) en de 400-gevallen in `BetalingControllerTests` (bedrag 0, drie decimalen, ongeldige IBAN) zijn tabellen in de vorm van losse methoden. `@ParameterizedTest` maakt ze korter en verhoogt N.
2. **Maintainable: zet `http.verify()` in een `afterEach` van `app.spec.ts`** en verdeel de 59 tests over `describe`-blokken per scherm (overzicht, betalen, sparen, beheer). Zonder `verify()` blijft een dubbel of onverwacht verzoek groen.
3. **Granular en Understandable: splits de twee mega-tests en geef de aantallen een naam.** Maak van `geeftDeDetailsVanEenTransactie` één test per detail of per transactie. Leid in `DemoDataTests` de 62, 133 en 63 af uit `DemoKlanten.ALLE` en `Generator`, zoals de test over de betalingen tussen klanten al doet.
4. **Repeatable: vervang `sleep(500)` en `isNotDone()` door een controle dat de tweede transactie echt op de lock wacht**, bijvoorbeeld via `pg_stat_activity` met `wait_event_type = 'Lock'`.
5. **Atomic (laag effect):** tests als `geeftOokDeSpaarrekeningNaDeBetaalrekening` en `jokertekensEnAanhalingstekensZijnGewoneTekens` voegen rijen toe aan de gedeelde DB van de klasse. Dat gaat goed omdat elke test unieke gebruikers gebruikt. Een helper die per test een verse rekeninghouder maakt, zou dat expliciet maken.

#### Methode

- Statisch/LLM-mix: 60/40. Als aanvulling op het statische bewijs voor T is in de git-historie geteld hoeveel commits test en code samen bevatten.
- LLM-model: claude-opus-5-5.
- Bestanden geanalyseerd: 26 van de 85, een deterministische steekproef van 30%. Geen bestand heeft meer dan 100 tests.
- Testmethoden geanalyseerd: 211 (3.892 regels).
- Talen en frameworks: Java (JUnit 5, jqwik, AssertJ, MockMvc, Testcontainers), TypeScript (Vitest met Angular TestBed, Playwright met een eigen DSL).
- Niet geanalyseerd, omdat de plugin ze niet ondersteunt: 5 bats-bestanden en 9 helm-unittest-bestanden.
- Niet gemeten: Predictive, Inspiring, Composable en Writable (uit Becks Test Desiderata).

## Is dit een goed resultaat?

Ja. Een 7,46 is "Good" op de schaal van de plugin, met 0,04 tekort voor "Excellent". Belangrijker dan het getal is *waar* de punten verloren gaan: bijna alleen op eigenschappen waar dit project bewust iets anders kiest, en op een paar concrete tests die je kunt verbeteren.

### Sterk: de eigenschappen die het zwaarst wegen

- **Understandable (9,2) en Maintainable (7,8)** wegen elk 1,5x. Dat ze hoog scoren, komt rechtstreeks uit de werkwijze. Testnamen zijn zinnen in domeintaal, zoals `weigertEenCursorVanEenAndereRekening`. Een scenario spreekt alleen de DSL en weet niets van HTTP of de browser (ADR 0031, gecontroleerd door `scripts/lagen-check.sh`). En in plaats van mocks gebruiken de tests fakes zoals `GeheugenGrootboek`: die controleren wat er gebeurt, niet welke methode werd aangeroepen.
- **First (8,7):** 89 van de 92 commits met productiecode bevatten ook tests. Dat past bij ATDD, waarin een scenario en een unittest eerst rood moeten zijn.
- **Geen enkel geval van tautology theatre** in 211 testmethoden. Dat is zeldzaam. Het past bij de mutatiescore van minimaal 95% (ADR 0018): een test die groen blijft als je de code weggooit, laat mutanten overleven, en PIT zou dat melden.

### Laag, maar bewust: Fast (5,0) en een deel van Repeatable (6,3)

De plugin ziet elke test die een database, een Spring-context of het cluster gebruikt als traag en als risico voor herhaalbaarheid. In dit project zijn dat bewuste lagen van de testpiramide:

- De repositorytests draaien tegen een echte PostgreSQL in Testcontainers (ADR 0009), omdat de belangrijkste regels in de database zitten: de trigger die een overboeking op nul laat uitkomen (ADR 0024), de locking in IBAN-volgorde (ADR 0026) en de foreign key die opname en maandafsluiting uit elkaar houdt (ADR 0039). Met een fake zou je die regels niet testen.
- De Playwright-scenario's zijn de acceptatietests van ATDD. Die horen tegen het geïnstalleerde systeem te draaien.

Deze tests zijn deterministisch: ze gebruiken een vaste klok (`Clock.fixed`, ADR 0013) en een vaste seed (`Random(7)`). Een betere score op Fast zou betekenen dat je die lagen weglaat, en dat is geen verbetering. Wie de index wil vergelijken met een project zonder databasetests, moet dit in gedachten houden: de 5,0 op Fast kost hier ongeveer 0,3 punt op de index.

De plugin meet Fast alleen uit de testcode: hij telt databases, Spring-contexten, HTTP-servers en `sleep`, en voert zelf niets uit. Een snellere pipeline verandert de score dus niet, ook al gaat het om dezelfde snelle feedback. Voorbeelden zijn de installatiescenario's in drie shards (ADR 0047), PIT per module (ADR 0048) en een acceptance stage die niet wacht op de backend-tests (ADR 0049). Hoe snel de tests in de praktijk feedback geven, lees je af aan de duur van de pipeline, niet aan deze score.

### Laag door de meetregel: Necessary (5,9)

De statische regel voor Necessary telt alleen geparametriseerde tests als positief signaal. Daardoor is de statische score 4,1, terwijl het taalmodel 8,5 geeft: er zijn geen tautologieën, geen uitgeschakelde tests en geen dubbele tests. De lage score zegt dus vooral dat een paar tabellen als losse methoden zijn geschreven (aanbeveling 1), niet dat er overbodige tests zijn.

#### Hoe de statische score voor Necessary ontstaat

De statische score is voor de helft een negatieve en voor de helft een positieve telling:

- **Negatief:** uitgeschakelde tests, lege tests, triviale asserts, frameworktests, mock-tautologieën en *redundante* tests. Redundant betekent hier: meerdere tests met hetzelfde assert-patroon op dezelfde methode.
- **Positief:** alleen geparametriseerde tests (`@ParameterizedTest`, jqwik-`@Property`, `it.each`).

Bij deze meting telde de plugin 6 negatieve en 9 positieve signalen op 211 testmethoden. Welke zes negatieve het waren, staat niet in het rapport. Uitgeschakelde tests of tautologieën waren het niet. Waarschijnlijk zijn het methoden die samen een tabel vormen, zoals de `zoektOp*`-tests.

De calculator van de plugin (`scoring.py`, functie `normalize_property`) zet beide tellingen om via een S-curve. Dat heeft twee gevolgen:

1. **De positieve helft levert pas iets op als ongeveer de helft van alle tests geparametriseerd is.** Bij 9 van de 211 draagt hij bijna niets bij.
2. **Een paar positieve signalen geven een lagere score dan geen enkel signaal.** Zonder signalen geeft de plugin vast een 5,0. Met 0 negatieve en 9 positieve signalen wordt het 4,3.

Met dezelfde calculator nagerekend:

| Negatief | Positief (van 211) | Statische score N |
| --- | --- | --- |
| 6 | 9 (deze meting) | 4,1 |
| 0 | 9 | 4,3 |
| 6 | 30 | 4,3 |
| 0 | 50 | 4,7 |
| 0 | 106 (de helft) | 6,7 |
| 0 | 160 | 8,6 |

De 4,1 betekent dus niet dat er overbodige tests zijn. Er is weinig geparametriseerd, en de schaal beloont pas als bijna alles geparametriseerd is.

#### Hoe je Necessary verhoogt

1. **Parametriseer wat nu een tabel in losse methoden is.** Dat haalt redundante tests weg en voegt geparametriseerde tests toe. Kandidaten:
   - de `zoektOp*`-tests in `JdbcTransactieRepositoryTests`;
   - de 400-gevallen in `BetalingControllerTests`: bedrag 0, drie decimalen, ongeldige IBAN.

   Voorbeelden van hoe het al gaat: de backend heeft 5 `@ParameterizedTest` en 7 `@Property`, en de frontend gebruikt `it.each` in `bedrag.spec.ts`, `datumregel.spec.ts` en `transactietype.spec.ts`.
2. **Schrijf meer property-tests met jqwik** waar een regel voor alle invoer geldt, zoals in `BedragTests` en `OverboekingTests`.

Parametriseer niet alleen om de score te halen. Een test die één gedrag controleert, wordt niet beter als je hem in een tabel met één rij zet. De statische score gaat dan omhoog, maar de test wordt minder leesbaar, en Understandable weegt 1,5x.

Realistisch brengen deze twee stappen de statische score naar 4,5 à 5, en Necessary van 5,9 naar ongeveer 6,2. Op de index is dat minder dan 0,05 punt. De echte winst is dat de tests korter worden, en dat een rode test duidelijker zegt welk geval fout is.

### Echte verbeterpunten

Wat overblijft, zijn concrete tests die beter kunnen. Geen van deze punten is een fout in de code; het zijn tests die bij een rode uitkomst minder duidelijk zijn, of te veel van de implementatie weten:

- **`app.spec.ts` mist `http.verify()`.** Dit is het belangrijkste punt: een extra of dubbel verzoek van de frontend, bijvoorbeeld een betaling die twee keer wordt verstuurd, valt in de unittests nu niet op. De Idempotency-Key in de backend vangt een dubbele betaling wel af (ADR 0027), maar de frontendtest hoort het zelf te zien.
- **De wachtende-locktests met `Thread.sleep(500)`** kunnen vals groen zijn op een trage machine. Ze zijn niet flaky, maar bewijzen minder dan ze lijken.
- **Twee mega-tests** (`geeftDeDetailsVanEenTransactie`, de test in `DemoDataTests` met `hasSize(62)`) en **strikte verwachtingen** in `KeycloakLoginsTests` maken een rode test lastiger te lezen en een refactor lastiger.

Met de eerste drie aanbevelingen komt de index waarschijnlijk boven de 7,5. Dat is een schatting; alleen een nieuwe meting laat het zien.

## Controle achteraf

De plugin zag een steekproef van 26 van de 85 testbestanden. Na het rapport zijn de belangrijkste bevindingen met `grep` gecontroleerd op alle tests:

- **`Thread.sleep` komt vaker voor dan het rapport noemt.** Behalve in `RenteOpslagTests` en `JdbcIdempotentieTests` staat hetzelfde patroon (`sleep(500)` en daarna `isNotDone()`) ook in `JdbcGrootboekTests`. In `GelijktijdigeRenteTests` staat een `sleep(100)`, maar die zit in een lus die `pg_stat_activity` vraagt of er iemand op een lock wacht. Dat is precies wat aanbeveling 4 voorstelt; die aanpak kan dus naar de andere drie tests worden overgenomen. In `BffTests` is de `sleep(2000)` het gedrag van de nep-`bank-api`: hij moet trager antwoorden dan de read-timeout. Dat is geen wachten in de test.
- **`app.spec.ts` heeft inderdaad geen enkele aanroep van `verify()`**, en telt 1072 regels.
- **`hasSize(62)`** staat in `DemoDataTests`, en **`geeftDeDetailsVanEenTransactie`** in `JdbcTransactieRepositoryTests`, zoals het rapport zegt.
- De mutatiedrempel van 95% staat als `mutationThreshold` in `backend/pom.xml`.

## Opnieuw meten

Draai in Claude Code `/msec:farley-score` en kies het hele project, of geef een map mee, bijvoorbeeld `/msec:farley-score backend/bank-api/src/test/java`. De installatie staat in het [README van de plugin](https://github.com/cd-training-courses/farley_score_plugin). Vergelijk een nieuwe meting vooral per eigenschap en per aanbeveling, niet alleen op de index: door de steekproef en het oordeel van het taalmodel kan de index een paar tienden schommelen zonder dat de tests veranderd zijn.
