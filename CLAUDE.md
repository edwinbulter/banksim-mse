# BankSim-MSE

BankSim opnieuw gebouwd volgens *Modern Software Engineering* (Farley). Het doel en de aanpak staan in [README.md](README.md). De volledige werkwijze staat in [doc/werkwijze-mse.md](doc/werkwijze-mse.md).

## ATDD-profiel

Werkwijze: skill `atdd` (`.claude/skills/atdd/`). Dit profiel bevat wat specifiek is voor dit project.

**Stack:** gelijk aan het origineel ([edwinbulter/banksim](https://github.com/edwinbulter/banksim)):
- Java 21 en Spring Boot 4 (Maven wrapper, Jib);
- Angular 22 met Vitest;
- Playwright TS met een interne DSL;
- Helm 4, kind en GitHub Actions.

Daarnaast zijn de IaC-testtools toegestaan: helm-unittest, kubeconform, conftest, bats, shellcheck, hadolint en actionlint. Die draaien in containers die op digest zijn vastgepind (ADR 0006). Een nieuwe library of tool alleen met een ADR.

**Commando's** (`make help` toont ze allemaal)
- Commit stage: `make commit-stage`. Afzonderlijk: `make backend-test`, `make mutatie` (PIT, ADR 0018), `make frontend-test`, `make iac`. Het PIT-rapport staat in `backend/<module>/target/pit-reports/index.html`; in CI is het artifact `pit-rapport`.
- Installeren + acceptatietests: `make acceptance`, dat `make install`, `make e2e` en `make e2e-infra` uitvoert.
- Installatiescenario's: `make e2e-infra` (project `infra`, zonder `@destructief`). In CI draaien ze met `BANKSIM_DESTRUCTIEF=1` en het nep-origineel.
- Eén scenario: `cd e2e && npx playwright test --project=rest specs/<naam>.spec.ts`. Gebruik `--project=chromium` voor de UI en `--project=infra` voor een installatiescenario. Een scenario met `@simulatiedatum` draait in `simulatiedatum-rest` (of `-chromium`), met `--no-deps`; anders draaien eerst alle gewone projecten (ADR 0014). Nooit meerdere `simulatiedatum-*`-projecten tegelijk met `--no-deps`: dan valt de keten weg, draaien ze parallel en wissen ze elkaars datum.
- Welke versie draait: `curl -sk https://bank-mse.localtest.me/api/versie`, of de voettekst. Dit is de image-tag: de korte commit-hash, eventueel met `-dirty-<tijdstempel>`.
- Pipeline: `gh run list --limit 3` en `gh run watch <id> --exit-status`. Bij falen: `gh api repos/edwinbulter/banksim-mse/actions/jobs/<job-id>/logs`.

**Waar komt wat**

| Wijziging | Waar | Test |
| --- | --- | --- |
| Scenario | `e2e/specs/` (infra: `e2e/specs/infra/`) | Playwright: projecten `rest` en `chromium` alles, `firefox` en `webkit` alleen `@ui` en `@rook` (ADR 0019); infra: `infra` |
| DSL / drivers | `e2e/dsl/`, `e2e/drivers/{rest,ui,shell}/` | Via de scenario's, plus `npm --prefix e2e run typecheck` |
| Domein | `backend/bank-domain` (zonder Spring) | JUnit, jqwik |
| API | `backend/bank-api` | MockMvc of slicetest; repository met Testcontainers; ArchUnit |
| BFF | `backend/bank-bff` | `@SpringBootTest` met MockMvc en een nep-`bank-api` (JDK-`HttpServer`) |
| Testdata (seed-CLI) | `backend/bank-seed` | JUnit; Keycloak met `MockRestServiceServer`; database met Testcontainers |
| Schema | `backend/bank-api/src/main/resources/db/migration` (Flyway) | Via de repositorytests van `bank-api` en `bank-seed` |
| UI | `frontend/src/app` | Vitest |
| Helm-chart | `deploy/banksim-mse/templates` | `deploy/banksim-mse/tests/*_test.yaml`, plus de namespacecheck in `scripts/iac-check.sh` |
| Scripts | `deploy/scripts` | `deploy/scripts/test/*.bats`, shellcheck |
| Pipeline | `.github/workflows/ci.yml` | actionlint |
| ADR | `doc/adr/NNNN-titel.md` | – |

**Vangrails** (skill §4; elk één keer rood gezien). De ArchUnit-tests staan in `backend/bank-api/src/test/java/nl/banksim/api/architectuur/` en controleren `bank-api` en `bank-domain`, niet `bank-seed` en `bank-bff`. Wat nog ontbreekt of nog geen rood heeft gezien, staat in [doc/backlog.md](doc/backlog.md).

| Vangrail | Test | Rood gezien |
| --- | --- | --- |
| Alleen `LedgerService` boekt (ADR 0024) | `AlleenLedgerServiceBoektTests` | ja, sabotage in 7e52e79 |
| Geen kringen tussen de features van `bank-api` en van `bank-domain`; records `*Json` worden alleen door controllers gebruikt (cohesie en koppeling) | `ModulegrenzenTests` | ja, sabotage per regel (fase 2.1) |
| Het grootboek kent de rente-code niet; die luistert naar `OverboekingGeboekt` (ADR 0030) | `GrootboekKentRenteNietTests` | ja, sabotage (S9.1, rentecorrectie) |
| Elk endpoint heeft `@PreAuthorize`; zonder token 401, als klant 403 op beheer (ADR 0022) | `ToegangPerEndpointTests`, `AlleEndpointsTests` | ja, e0cddec |
| Gelijktijdige overboekingen: geen deadlock, totaal behouden, nooit onder nul (ADR 0026) | `grootboek/GelijktijdigeOverboekingenTests` | ja, sabotage in 54eac60 |
| Een betaling met dezelfde Idempotency-Key boekt één keer; de sleutel geldt per gebruiker (ADR 0027) | `BetalingControllerTests`, `JdbcIdempotentieTests` | ja, sabotage in 8140ffd |
| Een overboeking heeft twee kanten die samen nul zijn (ADR 0024) | trigger in `db/migration/V6__grootboek.sql`, via `JdbcGrootboekTests` | ja, sabotage (fase 1 van de backlog) |
| Geen `double`/`float` voor bedragen (ADR 0010) | `GeenDrijvendeKommaTests` | ja, sabotage in bank-api en bank-domain (fase 1) |
| Geen systeemtijd buiten de klok (ADR 0013) | `GeenSysteemtijdTests` | ja, sabotage in bank-api en bank-domain (fase 1) |
| Geen `synchronized`, niet op een methode en niet als blok, in alle modules van de backend (concurrency) | `GeenSynchronizedTests` (bronscan) | ja, sabotage in bank-seed en bank-bff (fase 2.3) |
| Het domein kent geen framework (Spring, Jakarta, Jackson) | `bank-domain/.../architectuur/DomeinKentGeenFrameworkTests`; de pom heeft ook geen Spring | ja, sabotage: Spring in de pom en `@Component` (fase 2.2) |
| Mutatiescore minimaal 95% per module (ADR 0018) | PIT, `make mutatie` | – |
| Alles in namespace `banksim-mse`, geen geheimen in de chart (ADR 0004, 0009) | `scripts/iac-check.sh` (yq) | ja, sabotage: ClusterRole, andere namespace, letterlijk wachtwoord (fase 1) |
| Geen `parseFloat`, `parseInt`, `Number()` of unaire `+` voor bedragen (ADR 0010) | `frontend/eslint.config.js` | ja, sabotage: alle vijf vormen (fase 1) |

**Doelomgeving:** alleen kubectl-context `kind-single-node` en namespace `banksim-mse`, op de hosts `bank-mse.localtest.me` en `auth-mse.localtest.me` (Keycloak) (ADR 0004, 0007). Er komen geen cluster-brede resources. Het origineel in namespace `banksim` blijft onaangeroerd. Daarnaast is verboden:
- `kubectl edit` of `kubectl apply` met de hand;
- destructieve scenario's (`@destructief`) buiten het CI-cluster `kind-banksim-mse-ci`;
- geheimen in Git of in `values.yaml`.

**Afspraken**
- Node 24 via `PATH=/opt/homebrew/opt/node@24/bin:$PATH`. De `Makefile` doet dit zelf. Raak de systeem-Node niet aan.
- Commitprefix `[ECS-63]`, tenzij de gebruiker een andere noemt.
- Nooit committen of pushen zonder toestemming. Een push naar `main` start de pipeline.
- Taal: Nederlands, in de code (domeintermen), de specs, de ADR's en de documentatie.

**Valkuilen**
- `make e2e` test wat er op dat moment geïnstalleerd is. Na een codewijziging gebruik je `make acceptance`, en controleer je de versie.
- `make install` bouwt de images met `-DskipTests`. Draai dus eerst `make commit-stage`. De rooktests in `install.sh` controleren alleen of er status 200 terugkomt.
- `install.sh` maakt de Secrets met wachtwoorden één keer aan (ADR 0009). PostgreSQL zet ze alleen bij de eerste start op een lege PVC. Verwijder dus nooit los een Secret: gebruik `make uninstall`, dat de hele namespace met de PVC weghaalt.
- Tijdens een testrun draait per worker een pod `bank-seed-sessie-*` (ADR 0020). Na de run, ook na Ctrl-C, is hij weg. Het project `infra` start nog een pod per seed-opdracht.
- De shell-driver (`bank-seed`, installatiescenario's) gebruikt `BANKSIM_CONTEXT` en valt anders terug op `kind-single-node`. Elke CI-stap die hem gebruikt, heeft `BANKSIM_CONTEXT` nodig. Lokaal valt het ontbreken niet op.
- De eerste installatie duurt langer: Keycloak start, en de Job `keycloak-config` maakt daarna de realm aan.
- Een wijziging aan de realm hoort in `deploy/banksim-mse/files/realm-banksim-mse.json`. De Job `keycloak-config` past hem toe bij elke installatie, ook op een bestaande realm (ADR 0017). Geheimen in die JSON hebben de vorm `$(env:NAAM)`, niet `${NAAM}`. Draai na zo'n wijziging de scenario's eerst op je bestaande installatie: CI installeert altijd vers, en ziet een ontbrekend recht dus niet.
- De simulatiedatum geldt voor de hele bank. Blijft hij na een afgebroken run staan, dan falen andere scenario's op saldo of datum. De fixture wist hem na elk `@simulatiedatum`-scenario; controleer bij twijfel tabel `instelling` in database `bank`.
- De valkuilen van de tools (helm-unittest, yq, shellcheck, de webhook van ingress-nginx) staan in `.claude/skills/atdd/references/stack-helm-bats.md`.

**Backlog en achtergrond**
- `doc/werkwijze-mse.md`:
  - slices: hoofdstuk 7;
  - eisen die over de hele applicatie gaan: hoofdstuk 7, laatste deel;
  - losse backlogpunten (uitgesteld werk met reden): hoofdstuk 7, sectie "Losse backlogpunten";
  - ADR-backlog: hoofdstuk 6;
  - infrastructuur: hoofdstuk 9;
  - traceerbaarheid naar het origineel: hoofdstuk 11;
  - Definition of Done: hoofdstuk 12.
- Herstel van AI-slop (ontbrekende vangrails, opruimen): [doc/backlog.md](doc/backlog.md).
- Voortgang: de status in `README.md`, de bestaande specs en `@wip`-tags, en de bestaande ADR's in `doc/adr/`.
- Werken in een team (werk verdelen, samenwerkingsmomenten): `doc/teamwerk-mse.md`.
- Controle door de mens: `doc/werkwijze-mse.md` §10.1. Schrijf geen controle-documenten per slice meer; de oude staan in `doc/archief/`.
- Leren door de mens: `doc/werkwijze-mse.md` §10.2. Uitleg van een deel van het systeem: skill `uitleg` (`/uitleg <onderwerp>`).
