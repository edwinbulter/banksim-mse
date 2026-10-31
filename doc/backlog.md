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
2. **Vangrail (het domein kent geen framework): een ArchUnit-regel in `bank-domain`.** Geen afhankelijkheid van `org.springframework..` en `jakarta..`. De ArchUnit-dependency in `bank-domain/pom.xml` is nu ongebruikt; deze regel geeft hem een functie. Vandaag groen, dus sabotage.
3. **Vangrail (concurrency met de juiste primitieven): geen `synchronized`.**
   - Een ArchUnit-regel voor de modifier op methodes.
   - Een bronscan voor blokken, want ArchUnit ziet die niet; zie `stack-java-spring.md`.
   - Vandaag groen, dus sabotage.
4. **Vangrail: `GeenSysteemtijdTests` uitbreiden.**
   - Extra methodes: `LocalTime.now()`, `YearMonth.now()`, `Year.now()`, `Clock.system*()` buiten `KlokConfig`, en `System.currentTimeMillis()`.
   - Extra modules: `bank-seed` en `bank-bff`. Die vallen nu buiten elke ArchUnit-regel.
   - `SimulationClock.nu()` gebruikt `LocalTime.now(klok)`, en dat blijft toegestaan.
   - *(te verifiëren)* Of de uitbreiding vandaag rood wordt; anders is sabotage nodig.
5. **Vangrail (vierlaagse model): specs spreken alleen de DSL.**
   - Een script in `scripts/` met een bats-test, dus geen nieuwe tool. Zie `FourLayerTests` in atdd-casus-1.
   - Het controleert dat bestanden in `e2e/specs/` alleen uit `../dsl/` importeren, en geen `page`, `request` of `expect` gebruiken.
   - Vandaag groen, dus sabotage.

   **Besloten: drivers wachten en controleren het protocol, maar beweren niets over gedrag.** Dit gaat met een ADR mee in de commit van 2.5, en werkwijze hoofdstuk 5 wordt erop aangepast. Van de drie soorten `expect` mogen alleen de eerste twee in een driver.
   - **Wachten** mag, bijvoorbeeld `toBeVisible()` in een UI-driver. Dat is Playwrights manier om te wachten tot de pagina klaar is.
   - **Het protocol controleren** mag: een status 200 bij een GET of de vorm van een antwoord. Zo faalt de driver meteen met een duidelijke melding als hij het antwoord niet kan lezen.
   - **Een uitkomst die per scenario kan verschillen,** zoals of een betaling lukt of geweigerd wordt, beweert de driver niet. Hij geeft haar terug aan de DSL, zoals `zetSimulatiedatum(): Promise<Uitkomst>` al doet.

   Waarom: een driver wordt door veel scenario's gebruikt. Een ingebouwde verwachting dwingt dubbele methodes af (`neemOp` naast `probeerOpTeNemen`), kan per driver verschillen, valt buiten de ⛔-controle op goedgekeurde verwachtingen, en geeft een melding zonder domeintaal. De drivers die nu een uitkomst beweren, ruimt fase 3 op.
6. **Vangrail (frontend):** `"strict": true` expliciet zetten in `frontend/tsconfig.json`, en `strictTemplates` in `angularCompilerOptions`.

## Fase 3: slop opruimen

1. **Harde regel (hoge cohesie): validatie op één plek.**
   - Het probleem: een ongeldige IBAN of een ongeldig bedrag bij `POST /api/betalingen` geeft 500. Dat staat al in de losse backlogpunten. Hetzelfde geldt waarschijnlijk voor een bedrag ≤ 0, dezelfde rekening aan beide kanten (constructor van `Overboeking`) en een ontbrekend veld *(te verifiëren)*.
   - De oplossing: één `@RestControllerAdvice` die `IllegalArgumentException` als 400 met een probleembericht teruggeeft.
   - Begin met een scenario.
   - De bijna gelijke exception-handlers in `BetalingController` en `OverschrijvingController` gaan op in de advice.
2. **Harde regel (hoge cohesie): de saldoformule op één plek.** `openingssaldo + coalesce(sum(bedrag))` staat drie keer in SQL:
   - `JdbcGrootboek` (`<= :datum`);
   - `JdbcRekeningRepository` (`<= :vandaag`);
   - `JdbcRenteOpslag` (`< :van`, het saldo bij het begin van de dag; nagaan of dat bewust is).
3. **Harde regel (hoge cohesie): orkestratie uit de controller.**
   - `SimulatiedatumController` doet zelf twee mutaties: eerst `zet` of `wis`, daarna `sluitAf`. Er zit geen service tussen en geen `@Transactional`.
   - De oplossing: een service met een transactie.
4. **Harde regel (YAGNI en eenvoud): dubbele code samenvoegen.**
   - `ibanOf404` staat in `RenteController` en in `TransactieController`.
   - Het onschadelijk maken van LIKE-tekens staat in `JdbcContacten` en in `Zoekfilter`. Dat staat al in de losse backlogpunten.
   - De tabel van transactietype naar label staat in de frontend twee keer: `transactie/transactietype.ts` en `zoek-formulier.ts`.
   - De interface `Rekening` staat in de frontend twee keer: `overzicht.ts` en `overschrijven-scherm.ts`.
5. **Harde regel (geen luie quick-fixes):**
   - `Cursor.lees` vangt elke `RuntimeException` op. Zo verschijnt ook een fout in de HMAC-configuratie als 400. Vang alleen de parseerfouten.
   - `SeedCommando` negeert onbekende opties van `transacties` stil. Een tikfout geeft dan stilletjes minder testdata. Laat de opdracht falen.
   - `AlleEndpointsTests` controleert `isNotEqualTo(401)` voor de `permitAll`-endpoints. Daardoor komt een 500 erdoor.
   - In `Zoekfilter.bedrag` overleeft de PIT-mutant `>= 0` → `> 0`, omdat geen test een bedrag van 0 gebruikt. In de ene run wordt hij gedood, in de andere niet: 161 en 160 van de 162 bij dezelfde code. Een test met bedrag 0 maakt de score stabiel.
   - `deploy/scripts/uninstall.sh` heeft een `shellcheck disable=SC2086` zonder reden.
   - `subscribe` zonder foutafhandeling in `overschrijven-scherm.ts`, `betalen-scherm.ts` en `app.ts`. Een fout blijft dan onzichtbaar. De spaarrekening staat al in de losse backlogpunten.
6. **Harde regel (vierlaagse model): uitkomsten uit de drivers.** Volgens het besluit bij 2.5 beweert een driver geen uitkomst die per scenario kan verschillen. Zoek de `expect`'s in de drivers die dat wel doen, waarschijnlijk de 201 van betalen en overschrijven. Laat de driver een `Uitkomst` teruggeven en de DSL beweren. Dan kunnen dubbele methodes als `neemOp` en `probeerOpTeNemen` samengaan.
7. ⛔ **Beslissing (geen luie quick-fixes): de herkansing in CI.** `e2e/playwright.config.ts` heeft `retries: 1` in CI, en daardoor slaagt een flaky scenario stil. Er zijn twee keuzes:
   - `retries: 0`;
   - de pipeline laat een scenario dat pas bij de herkansing slaagt (status `flaky`) zien als waarschuwing.
8. ⛔ **Beslissing (geen luie quick-fixes): de PIT-uitsluiting van `nl.banksim.*Config`** (ADR 0018). Daardoor valt de rolmapping in `BeveiligingConfig` buiten mutation testing. Er zijn twee keuzes:
   - de mapping naar een eigen klasse halen, met een eigen test;
   - de uitsluiting houden, met een reden in ADR 0018.

## Fase 4: idempotente mutaties

1. **Vangrail (idempotentie): seed-opdracht `klant`.** `SeedKlantAanmaker` schrijft de rekeninghouder in de database vóór de login in Keycloak. Mislukt Keycloak, dan botst een herhaling op de UNIQUE-constraint van `gebruikersnaam`. Test: twee keer dezelfde klant geeft dezelfde toestand, of een duidelijke fout zonder half aangemaakte klant.
2. ⛔ **Beslissing (idempotentie): de seed-opdrachten `spaarrekening`, `transactie` en `transacties`** hebben geen business-sleutel. Elke herhaling maakt een nieuwe rekening of nieuwe transacties, en `transacties` boekt in een lus zonder transactie. Er zijn twee keuzes:
   - idempotent maken;
   - expliciet uitzonderen als testdata-CLI (ADR 0003), met wel één transactie per opdracht.
3. **Vangrail (idempotentie): "twee keer is één keer"-tests** waar ze ontbreken:
   - twee keer `PUT /api/beheer/simulatiedatum` en twee keer `DELETE`, op controllerniveau;
   - gelijktijdig `markeer` in `JdbcRenteOpslag`: de nachtelijke afsluiting tegelijk met een `PUT` van de beheerder;
   - de BFF stuurt de header `Idempotency-Key` door naar `bank-api`. Alleen een e2e-scenario bewijst dat nu.
   - de claim op de sleutel wordt teruggedraaid na een weigering, getest met Postgres op `BetaalService` en `OverschrijfService`. Dat staat al in de losse backlogpunten.

## Fase 5: YAGNI en modern Java

1. **Harde regel (YAGNI): ongebruikte code weg.**
   - ⛔ Productiecode die alleen tests gebruiken: `Zoekfilter.GEEN` en de constructor van `Overboeking` met vijf argumenten. Kies tussen twee wegen: verplaatsen naar een testhulp, of laten staan als bewuste gemaksconstructie.
   - De kolom `idempotency_sleutel.aangemaakt_op`. Hij heeft `DEFAULT now()`, dus de tijd van de database in plaats van de `SimulationClock`, en niemand leest hem.
   - De interface `RenteOpslag`: één implementatie en geen testdouble.
   - `prettier` in `frontend/package.json` en `.prettierrc`: geen script draait hem.
   - De verouderde `bank-domain/.../package-info.java`.
2. **Harde regel (modern Java):**
   - De probleemcodes in `BetalingGeweigerd` en `OngeldigZoekfilter` worden een enum in plaats van losse strings.
   - `BetaalService.betaal` met negen parameters krijgt een command-record.
3. *(te verifiëren)* **Harde regel (modern Java): één stijl voor ontbrekende waarden.** `Transactie`, `Zoekfilter`, `Partij` en `Boeking` gebruiken `Optional` in records, en `Overboeking` gebruikt nullable strings.

## Gevonden, maar geen slop

Deze punten vallen niet onder §4. Ze gaan naar de losse backlogpunten in werkwijze hoofdstuk 7.

- *(te verifiëren)* **De BFF heeft geen time-outs naar `bank-api`.** `ApiRoute.bankApiClient` bouwt zelf een `JdkClientHttpRequestFactory`, en dan werken `spring.http.clients.connect-timeout` en `read-timeout` uit `application.yaml` waarschijnlijk niet.
- **`VerzoekHash` kan botsen.** Hij plakt de velden aan elkaar met `|`, en een `null` wordt de tekst `"null"`. Twee verschillende verzoeken kunnen dan dezelfde hash krijgen, waardoor een 201 terugkomt in plaats van een 422.
- **`SimulationClock.nu()` leest de datum en de tijd los van elkaar.** Rond middernacht, zonder simulatiedatum, kan het tijdstip dan een dag verschuiven.
- **Het enum `Soort` mist `intern`,** terwijl de database die soort toestaat (`V11__rente.sql`).
- **Werkwijze hoofdstuk 5 loopt achter op de code:**
  - het noemt `big.js`, dat niet in `e2e/package.json` staat;
  - het noemt `dsl/aliassen.ts` en een project `setup`, die geen van beide bestaan.
