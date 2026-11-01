# Backlog: AI-slop herstellen

Slice 1 tot en met 9 zijn gebouwd met een skill `atdd` die nog geen kwaliteitsvangrails had. Daardoor kon AI-slop in de code komen zonder dat een test het tegenhield. Sinds de skill §4 heeft ("Geautomatiseerde kwaliteitsvangrails" en "Anti-AI-slop", overgenomen uit [atdd-casus-1](https://github.com/edwinbulter/atdd-casus-1)), gelden die regels ook achteraf. Deze backlog bevat wat al gedaan is en wat nog moet.

Elk punt is één iteratie volgens de skill `atdd`:
1. ⛔ de test of het scenario, en de verwachting;
2. rood gezien om de juiste reden;
3. ⛔ een ADR als dat nodig is;
4. lokaal verifiëren;
5. ⛔ commit.

Elk punt noemt de regel uit §4 en heeft een van twee soorten:
- **Vangrail:** een test in de commit stage. Overtreedt de code de regel al, dan maakt de echte slop hem rood. Een sabotage is dan niet nodig, en de vangrail komt samen met het herstel in één commit: push nooit een rode vangrail los. Is de vangrail vandaag groen, dan laat je hem met een sabotage één keer rood zien.
- **Harde regel:** een refactor onder de bestaande tests. De tests blijven groen. PIT controleert of ze de nieuwe code dekken.

Is een bevinding niet in de code gecontroleerd, dan staat er *(te verifiëren)* bij. Ga in dat geval eerst na of het klopt.

Het uitgestelde werk dat geen slop is, blijft in [werkwijze-mse.md](werkwijze-mse.md), hoofdstuk 7, sectie "Losse backlogpunten". Een punt dat daar al staat en hier is opgepakt, verdwijnt daar zodra het klaar is.

## Klaar

1. **Verbeterde skill `atdd` overgenomen** uit atdd-casus-1:
   - §4, vangrails en anti-AI-slop;
   - in `stack-java-spring.md` de ArchUnit-regels en de valkuilen;
   - in `walking-skeleton.md` de stap "vangrails eerst";
   - in het profielsjabloon de tabel Vangrails;
   - in het rapport (§6) een blok Resilience.

   De skill `uitleg` is niet overgenomen. Zijn regel "altijd Nederlands" bestaat alleen omdat de casus Engels is.
2. **Profiel aangevuld:** de tabel Vangrails in [CLAUDE.md](../CLAUDE.md), met per vangrail de test en het bewijs dat hij één keer rood was.
3. **Deze backlog.**
4. **Al aanwezig en rood gezien:**
   - alleen `LedgerService` boekt (`AlleenLedgerServiceBoektTests`, sabotage in 7e52e79);
   - toegang per endpoint (`ToegangPerEndpointTests`, `AlleEndpointsTests`, e0cddec);
   - gelijktijdige overboekingen in IBAN-volgorde (`GelijktijdigeOverboekingenTests`, sabotage in 54eac60);
   - idempotente betalingen en overschrijvingen met een Idempotency-Key (ADR 0027, `BetalingControllerTests`, `OverschrijvingControllerTests`).

## Fase 0: eerst S9.1 afmaken

1. **Klaar: de losser gemaakte asserties zijn weer exact.**
   - `verwachtSpaarmutatie` vergelijkt weer de hele mutatie, nu met de omschrijving: "Rente januari" voor de rente van de maand van de datum, anders "Inleg" of "Opname".
   - De UI-driver leidt het soort af uit een vaste tabel (`Rentecorrectie` → Rente) in plaats van `startsWith`.
   - De terugval `omschrijving ?? tegenpartij` in de REST-driver blijft. Hij volgt dezelfde regel als het scherm (`spaarrekening-scherm.ts`).
   - Sabotage: met de verwachte omschrijving "Overschrijving" in plaats van "Inleg" werd "Inleggen" rood.
2. **Klaar: S9.1 af**, met de rentecorrectie volgens ADR 0030 (geaccepteerd), en de nieuwe vangrail `GrootboekKentRenteNietTests`, rood gezien met sabotage. Twee open punten uit ADR 0030 staan in de losse backlogpunten: nooit rood ná de correcties, en een mogelijke deadlock met de nachtelijke afsluiting.

## Fase 1: bestaande vangrails bewijzen

1. **Klaar: elke bestaande vangrail is één keer rood gezien.** De sabotage is telkens weer weggehaald.
   - De trigger in `V6__grootboek.sql`, uitgezet: vier tests in `JdbcGrootboekTests` werden rood (één kant, drie kanten, som niet nul, dezelfde rekening).
   - `GeenDrijvendeKommaTests` en `GeenSysteemtijdTests`: een veld `double` en een aanroep van `LocalDate.now()`, in `bank-api` en in `bank-domain`.
   - `scripts/iac-check.sh`: een `ClusterRole`, een `ConfigMap` in namespace `banksim` en een letterlijk `DB_PASSWORD` werden elk tegengehouden.
   - De eslint-regels van ADR 0010: `parseFloat`, `parseInt`, `Number()`, `Number.parseFloat` en de unaire `+` gaven elk een fout.
2. **Klaar: de werkwijze is gelijkgetrokken met de skill.** De DoD in hoofdstuk 12 eist nu dat elke nieuwe vangrail één keer rood is gezien. Hoofdstuk 10 verwijst naar §6 van de skill, en hoofdstuk 6 naar deze backlog.

## Fase 2: ontbrekende vangrails

1. **Klaar: `ModulegrenzenTests` (hoge cohesie, lage koppeling).** Drie ArchUnit-regels: geen kringen tussen de featurepakketten van `bank-api`, geen kringen tussen die van `bank-domain`, en een record `*Json` wordt alleen door controllers gebruikt. De code is per feature ingedeeld, dus `layeredArchitecture` uit het sjabloon past niet. Alle drie waren groen op de bestaande code. Sabotage maakte elke regel rood: `simulatie` → `rente` (een kring via `grootboek`), `geld` → `grootboek` in het domein, en een klasse in `betaling` die `BetalingJson` gebruikt.
2. **Klaar: `DomeinKentGeenFrameworkTests` (het domein kent geen framework).** Een ArchUnit-regel in `bank-domain`: geen afhankelijkheid van `org.springframework..`, `jakarta..`, `javax..` en `com.fasterxml.jackson..`. De ArchUnit-dependency in die module heeft daarmee een functie. Sabotage: `spring-context` in de pom en `@Component` op een domeinklasse maakte de regel rood.
3. **Klaar: `GeenSynchronizedTests` (concurrency met de juiste primitieven).** Eén bronscan in bank-api over `src/main/java` van alle modules van de backend vindt het woord `synchronized`, op een methode en als blok. Een aparte ArchUnit-regel voor de modifier is niet nodig, want de scan vindt die ook (YAGNI). Sabotage: een blok in bank-seed en een methode in bank-bff werden allebei gemeld, met bestand en regel.
4. **Klaar: `GeenSysteemtijdTests` uitgebreid.** Nieuw verboden zijn `LocalTime`, `YearMonth` en `Year.now()` (ook met `ZoneId`) en `System.currentTimeMillis()`. Een tweede regel staat `Clock.system*()` alleen toe in `KlokConfig`. bank-seed en bank-bff hebben een eigen kopie, met ArchUnit als testdependency, en verbieden de systeemklok helemaal. Alles was groen op de bestaande code. Sabotage: de vijf nieuwe vormen in bank-api, `LocalDate.now()` in bank-seed en `Clock.systemUTC()` in bank-bff werden alle zeven gemeld.
5. **Klaar: specs spreken alleen de DSL (vierlaagse model), ADR 0031.** `scripts/lagen-check.sh` controleert dat een spec alleen uit `dsl/` importeert en geen `page`, `request` of `expect` gebruikt. Hij draait in `make commit-stage` en in de frontendjob van CI. `scripts/test/lagen-check.bats` test de controle in de bats-stap van `iac-check.sh`. Sabotage: zonder importcontrole, zonder de controle op `page`/`request`/`expect`, zonder de controle op een lege map en met een te ruim patroon werd telkens een bats-test rood. Een `expect` in een echte spec werd gemeld met bestand en regel.

   **Besloten (ADR 0031): drivers wachten en controleren het protocol, maar beweren geen uitkomst die per scenario kan verschillen.** Van de drie soorten `expect` in de drivers mogen er twee blijven:
   - **Wachten** mag, bijvoorbeeld `toBeVisible()` in een UI-driver.
   - **Het protocol controleren** mag: een status 200 bij een GET of de vorm van een antwoord.
   - **Een uitkomst,** zoals of een betaling lukt, geeft de driver terug aan de DSL. Dat ruimt fase 3 op.

   Werkwijze hoofdstuk 5 is erop aangepast. Dat er geen uitkomst in een driver staat, is niet automatisch te controleren; dat blijft een punt voor de review.
6. **Klaar: strenge typering in de frontend expliciet.** `frontend/tsconfig.json` zet `"strict": true` en `"strictTemplates": true`. Onder TypeScript 6 en Angular 22 zijn beide al de standaard, dus de code was al streng getypeerd en bleef groen. Maar dan hing de vangrail aan een standaardwaarde die bij een upgrade of een gekopieerde instelling stil kan veranderen. Sabotage: `[rekeningen]="42"` in een template, en een parameter zonder type plus `null` in een `string` in een component en in een spec. Alle drie waren rood in de build of in Vitest. Met `strictTemplates: false` of `strict: false` bleven ze groen. Een test op de instelling zelf is er niet: wie hem uitzet, verzwakt een vangrail, en dat vraagt ⛔ en een ADR.

## Fase 3: slop opruimen

1. **Klaar: validatie op één plek.** Scenario "Een betaling van € 0,00 wordt geweigerd" in `betalen.spec.ts`, via REST en de UI. Het was rood om de goede reden: een 500, en in de UI geen melding.
   - `ProbleemAdvice` maakt van elke `IllegalArgumentException` een 400 met probleemtype `ongeldige-invoer` en de melding van de exception. Dat geldt dus ook voor een ongeldige IBAN of een bedrag als "abc".
   - De meldingen in `Overboeking` zijn klanttaal: "Het bedrag moet groter zijn dan € 0,00." en "Van en naar zijn dezelfde rekening.".
   - De dubbele handlers van `BetalingController` en `OverschrijvingController` staan nu in `BetalingAdvice`, alleen voor die twee controllers. De exceptions blijven package-private in `betaling`.
   - Kritisch punt: ook een `IllegalArgumentException` door een fout in de bank zelf wordt nu een 400. Gooi er daarom alleen een bij ongeldige invoer.
   - Een bedrag als "abc" in de UI (een fout in de template van het controlescherm) staat in de losse backlogpunten.
2. **Klaar: de saldoformule op één plek.** `grootboek/Saldi` geeft het saldo aan het eind van een datum: het openingssaldo plus de transacties tot en met die datum. `JdbcGrootboek`, `JdbcRekeningRepository` en `JdbcRenteOpslag` gebruiken hem.
   - De `< :van` in `JdbcRenteOpslag` was bewust: het saldo bij het begin van de eerste dag. Dat is nu `aanHetEindVan(iban, van − 1 dag)`.
   - Het overzicht vraagt nu per rekening een saldo op, in plaats van één query voor alle rekeningen. Een klant heeft een paar rekeningen; optimaliseren pas na meten.
   - `JdbcRenteOpslag` had geen eigen repositorytest. `JdbcRenteOpslagTests` legt de dagsaldi eerst vast: het beginsaldo telt de rente van vóór de eerste dag mee en die dag zelf niet, en de rente van de maand zelf telt niet mee (ADR 0030). Sabotage: `<=` in plaats van `<`, en zonder `type <> 'RENTE'`; beide rood.
3. **Klaar: orkestratie uit de controller.** `SimulatieService` zet of wist de simulatiedatum en sluit daarna de maanden af; `SimulatiedatumController` roept alleen de service aan.
   - Besloten: **geen transactie om het geheel**, anders dan eerst voorgesteld. Elke maand van elke spaarrekening wordt al in een eigen transactie afgesloten (`MaandRente.boek`), en de afsluiting is idempotent (ADR 0028). Eén transactie om alles zou alle spaarrekeningen tegelijk vergrendelen, en bij één fout ook de datum terugdraaien.
   - Elke overboeking blijft één atomaire transactie: `LedgerService.boek` schrijft beide kanten, en de trigger uit V6 controleert bij de commit dat ze samen nul zijn.
   - `SimulatieServiceTests` bewaakt de volgorde. Sabotage: eerst afsluiten en dan pas de datum zetten maakte deze test en `SimulatiedatumControllerTests` rood.
4. **Klaar: dubbele code samengevoegd.** Telkens eerst een test voor de gedeelde plek, daarna de aanroepers omgezet onder de bestaande tests.
   - `rekening/IbanInPad`: een ongeldige IBAN in het pad is een 404 (ADR 0023), voor `RenteController` en `TransactieController`. Dat is sinds fase 3.1 belangrijker: zonder deze klasse wordt het een 400 via `ProbleemAdvice`.
   - `sql/Like.letterlijk`: `%`, `_` en `\` als gewone tekens, voor `JdbcContacten` en `Zoekfilter`. Het losse backlogpunt is weg.
   - `TRANSACTIETYPEN` in `transactie/transactietype.ts`: de 7 typen met hun label in de volgorde van het FO, voor de details en het zoekformulier.
   - De interface `Rekening` staat alleen nog in `overzicht.ts`; `overschrijven-scherm.ts` importeert hem.
5. **Harde regel (geen luie quick-fixes):**
   - **Klaar:** `Cursor.lees` vangt alleen nog de parseerfouten (`IllegalArgumentException`, `DateTimeParseException`) en controleert punt en komma zelf. Een fout in de HMAC-configuratie is geen 400 meer. Nieuwe test: een geldig ondertekende inhoud zonder datum, id of komma blijft een `OngeldigeCursor`. Sabotage: zonder `DateTimeParseException` in de catch, of zonder de kommacontrole, was die test rood.
   - **Klaar:** `SeedCommando` weigert een onbekende optie, bij alle zes de commando's en niet alleen bij `transacties`, voordat er iets geboekt wordt. De test was eerst rood voor alle zes.
   - **Klaar:** `AlleEndpointsTests` eist voor een `permitAll`-endpoint nu: geen 401 of 403, en ook geen 5xx. Sabotage: een `ResponseStatusException(500)` in `VersieController` was met de oude bewering groen en met de nieuwe rood.
   - **Klaar:** `ZoekfilterTests.eenBedragVanNulMag` doodt de grensmutant in `Zoekfilter.bedrag`. Met `> 0` in plaats van `>= 0` was de test rood.
   - **Klaar:** `uninstall.sh --purge` geeft de images als array door, zonder `shellcheck disable`. Nieuwe test `deploy/scripts/test/purge.bats`. Sabotage: alle images als één argument maakte hem rood.
   - **Klaar (⛔ teksten goedgekeurd):** geen `subscribe` meer zonder foutafhandeling in `overschrijven-scherm.ts`, `betalen-scherm.ts` en `app.ts`.
     - De rekeningen laden mislukt: "De rekeningen konden niet worden geladen.".
     - Betalen of overschrijven mislukt zonder melding van de API (500, netwerk): "Er ging iets mis. Probeer het later opnieuw." (`fout.ts`, `meldingBij`).
     - De suggesties laden mislukt: geen suggesties en geen melding; betalen kan gewoon verder.
     - Uitloggen mislukt: "Uitloggen is mislukt. Probeer het opnieuw.", en de klant blijft ingelogd.
     - Vijf Vitest-tests in `app.spec.ts`, eerst rood: vier op de ontbrekende melding, en de suggesties op een niet-afgevangen fout in de testrun. De spaarrekening staat nog in de losse backlogpunten.
6. **Klaar: uitkomsten uit de drivers (ADR 0031).** `betaal`, `betaalDubbel`, `legIn` en `neemOp` geven een `Uitkomst` terug ('gelukt' of 'geweigerd'); `probeerTeBetalen` en `probeerOpTeNemen` zijn weg. De DSL beweert: `betaalt`, `betaaltDubbel`, `legtIn` en `neemtOp` verwachten 'gelukt', met het probleemtype in de melding; `probeertTeBetalen` en `probeertOpTeNemen` onthouden de uitkomst voor `verwachtWeigering`.
   - De REST-driver controleert alleen nog het protocol: 201 is gelukt, 400 of 422 met een probleemtype is geweigerd, elke andere status is een fout. De UI-driver wacht op de rekening of op een melding.
   - Wat in de drivers blijft, is wachten en het protocol controleren: een 200 bij een GET, de vorm van een pagina, de inlog- en uitlogstappen.
   - Sabotage: Sanne tijdelijk geen contact in "Betaling naar een contact". Beide drivers faalden met dezelfde melding: "betaling van Jan (onbekend-contact)", Expected "gelukt", Received "geweigerd".
7. **Klaar (⛔ besloten): geen herkansing in CI.** `e2e/playwright.config.ts` heeft nu `retries: 0`, ook in CI. In de 24 geslaagde runs van 25 tot en met 31 oktober was geen enkel scenario `flaky`; de herkansing hielp dus nergens, maar zou het volgende flaky scenario stil laten slagen. Nu maakt het de pipeline rood. De andere keuze, een waarschuwing bij `flaky`, vroeg een extra reporter en stap in CI voor een probleem dat niet voorkwam (YAGNI). De zin over `retries: 1` in de Gevolgen van ADR 0014 is daarmee achterhaald; de ADR zelf blijft ongewijzigd.
8. **Klaar (⛔ besloten): de rolmapping in een eigen klasse.** Van alle `*Config`-klassen had alleen `BeveiligingConfig` van bank-api echte logica: de omzetting van `realm_access.roles` naar rechten met `ROLE_`. Die staat nu in `beveiliging/RealmRollen`, met `RealmRollenTests` (gewone JUnit, zonder Spring) voor de vier gevallen: rollen, geen `realm_access`, geen `roles`, en `roles` die geen lijst is. Dat laatste geval was niet getest. PIT meet de klasse: vier mutanten, alle vier gedood. De uitsluiting van `*Config` blijft voor de bedrading; de reden in ADR 0018 klopt nog.
   - Sabotage: `SCOPE_` in plaats van `ROLE_` maakte `RealmRollenTests` en `TokenControleTests` rood.
   - Eerst is de code leesbaarder geschreven: een positieve `instanceof` in plaats van `if (!(… instanceof …)) return …;`.

## Fase 4: idempotente mutaties

1. **Klaar: seed-opdracht `klant` atomair (⛔ besloten: een duidelijke fout bij herhaling, geen echte idempotentie).** `SeedKlantAanmaker` schrijft de rekeninghouder en maakt de login in één databasetransactie. Mislukt Keycloak, dan wordt de rekeninghouder teruggedraaid. Een herhaling stopt vóór Keycloak met "Klant jan bestaat al", zonder dubbele data. `SeedKlantAanmakerDatabaseTests` (PostgreSQL) was eerst rood: na een mislukte login stond de rekeninghouder er nog (`count` 1), en een herhaling gaf een kale `DuplicateKeyException`. Echte idempotentie (een herhaling maakt de klant af) is niet gekozen: de e2e-tests gebruiken unieke aliassen, dus het komt niet voor (YAGNI).
   - Eén klein gat blijft: mislukt de commit van de database ná een gelukte login, dan blijft er een login zonder rekeninghouder.
2. **Klaar (⛔ besloten): `spaarrekening`, `transactie` en `transacties` blijven gewone opdrachten.** Ze hebben geen business-sleutel; twee keer is bewust twee keer, zoals twee keer hetzelfde bedrag inleggen. Dat staat in het Javadoc van `SeedCommando`. Wel boekt `transacties` nu alles of niets (`JdbcTransacties.boekAlle`, één databasetransactie). `JdbcTransactiesTests.boekAlleBoektNietsAlsEenVanDeBoekingenFaalt` was eerst rood: de eerste boeking bleef staan.
3. **Klaar: "twee keer is één keer"-tests** waar ze ontbraken. Ze waren groen op de bestaande code; elk is met sabotage rood gezien.
   - Twee keer `PUT` en twee keer `DELETE /api/beheer/simulatiedatum` (`SimulatiedatumControllerTests`), en twee keer wissen tegen PostgreSQL (`JdbcSimulatiedatumRepositoryTests`). Sabotage: `wis` faalt als er niets te wissen is.
   - Gelijktijdig `markeer` in `JdbcRenteOpslagTests`: de tweede transactie wacht op de unieke sleutel van de eerste en krijgt daarna `false`. Sabotage: zonder `ON CONFLICT DO NOTHING` een `DuplicateKeyException`.
   - De BFF stuurt `Idempotency-Key` ongewijzigd door (`BffTests`). Sabotage: de header weghalen in `ApiRoute`.
   - Na een weigering kan dezelfde sleutel opnieuw, voor betalen en overschrijven, tegen PostgreSQL met de echte services (`SleutelNaWeigeringTests`). Sabotage: de claim in een eigen transactie (`REQUIRES_NEW`) gaf bij de tweede poging 422 "sleutel-al-gebruikt". Het losse backlogpunt daarover is weg.

## Fase 5: YAGNI en modern Java

1. **Klaar: ongebruikte code weg (YAGNI).**
   - De interface `RenteOpslag` is weg: één implementatie en geen testdouble. De klasse met de JDBC-code heet nu zelf `RenteOpslag` (test: `RenteOpslagTests`).
   - `prettier` en `.prettierrc` zijn weg uit de frontend; geen script draaide hem.
   - `bank-domain/.../package-info.java` beschrijft wat er nu in het domein zit, in plaats van het walking skeleton.
   - ⛔ Besloten: **laten staan.** `Zoekfilter.GEEN` en de constructor van `Overboeking` met vijf argumenten worden alleen door tests gebruikt, maar drukken een begrip uit ("geen filter", "een overboeking zonder omschrijving"). Naar een testhulp verplaatsen voegt een omweg toe. Geen slop.
   - ⛔ Besloten: **laten staan.** De kolom `idempotency_sleutel.aangemaakt_op` leest nu niemand, maar het opruimen van oude sleutels (losse backlogpunten) heeft hem nodig. Dat hij de tijd van de database gebruikt in plaats van de `SimulationClock`, klopt daarvoor: bewaren is technisch, geen banktijd.
2. **Klaar: modern Java.**
   - De probleemcodes zijn enums: `Weigering` (betaling) en `ZoekProbleem` (transactie), met de code van het probleemtype letterlijk per waarde, want de API belooft hem. Een tikfout in een `new BetalingGeweigerd("…")` kan niet meer.
   - Drie codes van het zoekfilter (`ongeldig-bedrag`, `ongeldig-type`, `ongeldige-richting`) werden alleen in `ZoekfilterTests` als tekst gecontroleerd; die test gebruikt nu de enum. `TransactieControllerTests.eenOngeldigVeldGeeft400MetZijnProbleemtype` controleert ze nu als URN. Sabotage: "ongeldig-richting" in de enum maakte hem rood.
   - `BetaalService.betaal` krijgt een `Betaalopdracht` (record) in plaats van negen parameters.
3. **Klaar (⛔ besloten: zo laten): de stijl voor ontbrekende waarden.** `Partij`, `Zoekfilter` en `Pagina` gebruiken `Optional` als veld van een record; `Overboeking` gebruikt `null` voor omschrijving, betalingskenmerk en extra omschrijving. Dat verschil is bewust: `Overboeking` volgt de kolommen die in de database `null` mogen zijn en het FO ("indien aanwezig"), en zijn Javadoc zegt dat. Alles naar één stijl omzetten raakt veel code voor weinig winst.

## Fase 6: geautomatiseerde controles vóór productie (Farley)

Uit de vergelijking met Farley (31 oktober): vertrouw niet op mensen die code lezen, maar op linters, typecontrole, architectuurchecks en contracttests in de pipeline. Linters, typecontrole en architectuurchecks zijn er grotendeels (fase 1 en 2). Dit zijn de gaten.

1. **Klaar (ADR 0032): mutatietests parallel aan de acceptance stage.** De acceptatie wachtte op PIT (8:40), terwijl de andere jobs van de commit stage na hooguit 1:24 klaar waren. De pipeline duurde zo ongeveer 15 minuten. Nu wacht de acceptatie alleen op `backend`, `frontend`, `infrastructuur` en `images`, en duurde de eerste pipeline daarna 7 minuten (run 41244343247; PIT 6:40, acceptatie 5:26). Een rode PIT-job maakt de pipeline nog steeds rood.
2. ⛔ **Beslissing (vangrail): contracttests op de grenzen tussen services.** Elke kant test nu tegen zijn eigen aanname van de andere kant:
   - `BffTests` gebruikt een nep-bank-api (JDK-`HttpServer`); niets controleert dat die zich gedraagt als de echte.
   - De Vitest-tests van de frontend verzinnen zelf de JSON van de API.

   Een breuk ziet nu alleen de acceptance stage. Kies een vorm, bijvoorbeeld:
   - één OpenAPI-specificatie (contract-first, al op de ADR-backlog in werkwijze hoofdstuk 6), met een test die bank-api ertegen controleert en de nep en de frontendfixtures ertegen valideert;
   - of een contracttest per consument.

   Nieuwe tool, dus een ADR. Draai de contracttests zonder Spring-context waar het kan, dan maken ze PIT niet trager. Op de doorlooptijd scheelt het verder niets: ze lopen in de jobs `backend` en `frontend`, die ruim binnen PIT klaar zijn.
3. ⛔ **Beslissing (vangrail): statische analyse van Java.** Er is geen Error Prone, SpotBugs of Checkstyle. Fouten die de compiler niet ziet, zoals een genegeerde returnwaarde of `equals` zonder `hashCode`, vallen alleen op als een test ze toevallig raakt. Kies een tool en de regels. Nieuwe tool, dus een ADR.
4. **Klaar: architectuurregels voor bank-bff en bank-seed.** Er stond al: geen systeemtijd (fase 2.4) en geen `synchronized` (bronscan, fase 2.3). Dat bank-bff niets van het domein en van bank-seed weet, en dat bank-api niets van bank-seed weet, dwingt Maven af: die afhankelijkheden bestaan niet. Twee gaten zijn gedicht:
   - `GeenDrijvendeKommaTests` (ADR 0010) ook in bank-seed en bank-bff. bank-seed zet saldi en transacties klaar. Sabotage: een `double`-veld in `SeedCommando` en een `double`-parameter in `ApiRoute`, beide rood.
   - `AlleenOpslagKentSqlTests` in bank-seed: alleen de klassen `Jdbc*` (en de applicatie, die ze opbouwt) gebruiken `JdbcClient`. Sabotage: een `JdbcClient`-veld in `SeedCommando`, rood.
   - Geen verdere regels voor bank-bff: het is een doorgeefluik zonder eigen logica (ADR 0008). Wat daar bewaakt moet worden, is gedrag (`BffTests`), geen indeling.
   - Niet gekozen: "bank-seed boekt via de domeinregels" als regel. bank-seed gebruikt `Bedrag` en `Iban` uit het domein, en de geldregel dekt de rest. Een externe transactie heeft maar één kant in de bank, dus `Overboeking` past daar niet.
5. ⛔ **Beslissing: de skill `atdd` aanvullen.** De skill noemt architectuurchecks expliciet, maar linters en typecontrole alleen per stack en contracttests helemaal niet. Toch staat in §1 stap 4 dat test doubles op echte grenzen blijven. Voorstel:
   - §4, "Vangrails eerst": ook een linter en een strenge typecontrole per taal, in de commit stage;
   - §1 stap 4 of §4: elke test double op een grens tussen services heeft een contracttest;
   - `references/profiel-sjabloon.md`: een vraag naar de grenzen tussen services en het contract dat elke grens bewaakt.

   De skill komt uit atdd-casus-1. Bepaal of de aanvulling daar ook naartoe gaat.

## Gevonden, maar geen slop

Deze punten vallen niet onder §4. Ze gaan naar de losse backlogpunten in werkwijze hoofdstuk 7.

- *(te verifiëren)* **De BFF heeft geen time-outs naar `bank-api`.** `ApiRoute.bankApiClient` bouwt zelf een `JdkClientHttpRequestFactory`, en dan werken `spring.http.clients.connect-timeout` en `read-timeout` uit `application.yaml` waarschijnlijk niet.
- **`VerzoekHash` kan botsen.** Hij plakt de velden aan elkaar met `|`, en een `null` wordt de tekst `"null"`. Twee verschillende verzoeken kunnen dan dezelfde hash krijgen, waardoor een 201 terugkomt in plaats van een 422.
- **`SimulationClock.nu()` leest de datum en de tijd los van elkaar.** Rond middernacht, zonder simulatiedatum, kan het tijdstip dan een dag verschuiven.
- **Het enum `Soort` mist `intern`,** terwijl de database die soort toestaat (`V11__rente.sql`).
- **Werkwijze hoofdstuk 5 loopt achter op de code:**
  - het noemt `big.js`, dat niet in `e2e/package.json` staat;
  - het noemt `dsl/aliassen.ts` en een project `setup`, die geen van beide bestaan.
