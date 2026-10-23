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
- Commit stage: `make commit-stage`. Afzonderlijk: `make backend-test`, `make frontend-test`, `make iac`.
- Installeren + acceptatietests: `make acceptance`, dat `make install`, `make e2e` en `make e2e-infra` uitvoert.
- Installatiescenario's: `make e2e-infra` (project `infra`, zonder `@destructief`). In CI draaien ze met `BANKSIM_DESTRUCTIEF=1` en het nep-origineel.
- Eén scenario: `cd e2e && npx playwright test --project=rest specs/<naam>.spec.ts`. Gebruik `--project=chromium` voor de UI en `--project=infra` voor een installatiescenario. Een scenario met `@simulatiedatum` draait in `simulatiedatum-rest` (of `-chromium`, `-firefox`, `-webkit`), met `--no-deps`; anders draaien eerst alle gewone projecten (ADR 0014).
- Welke versie draait: `curl -sk https://bank-mse.localtest.me/api/versie`, of de voettekst. Dit is de image-tag: de korte commit-hash, eventueel met `-dirty-<tijdstempel>`.
- Pipeline: `gh run list --limit 3` en `gh run watch <id> --exit-status`. Bij falen: `gh api repos/edwinbulter/banksim-mse/actions/jobs/<job-id>/logs`.

**Waar komt wat**

| Wijziging | Waar | Test |
| --- | --- | --- |
| Scenario | `e2e/specs/` (infra: `e2e/specs/infra/`) | Playwright: projecten `rest`, `chromium`, `firefox`, `webkit`; infra: `infra` |
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
- De shell-driver (`bank-seed`, installatiescenario's) gebruikt `BANKSIM_CONTEXT` en valt anders terug op `kind-single-node`. Elke CI-stap die hem gebruikt, heeft `BANKSIM_CONTEXT` nodig. Lokaal valt het ontbreken niet op.
- De eerste installatie duurt langer: Keycloak start, en de Job `keycloak-config` maakt daarna de realm aan.
- Een wijziging aan de realm hoort in `deploy/banksim-mse/files/realm-banksim-mse.json`. De Job `keycloak-config` past hem toe bij elke installatie, ook op een bestaande realm (ADR 0017). Geheimen in die JSON hebben de vorm `$(env:NAAM)`, niet `${NAAM}`. Draai na zo'n wijziging de scenario's eerst op je bestaande installatie: CI installeert altijd vers, en ziet een ontbrekend recht dus niet.
- De simulatiedatum geldt voor de hele bank. Blijft hij na een afgebroken run staan, dan falen andere scenario's op saldo of datum. De fixture wist hem na elk `@simulatiedatum`-scenario; controleer bij twijfel tabel `instelling` in database `bank`.
- De valkuilen van de tools (helm-unittest, yq, shellcheck, de webhook van ingress-nginx) staan in `.claude/skills/atdd/references/stack-helm-bats.md`.

**Backlog en achtergrond**
- `doc/werkwijze-mse.md`:
  - slices: hoofdstuk 7;
  - eisen die over de hele applicatie gaan: hoofdstuk 7, laatste deel;
  - ADR-backlog: hoofdstuk 6;
  - infrastructuur: hoofdstuk 9;
  - traceerbaarheid naar het origineel: hoofdstuk 11;
  - Definition of Done: hoofdstuk 12.
- Voortgang: de status in `README.md`, de bestaande specs en `@wip`-tags, en de bestaande ADR's in `doc/adr/`.
- Werken in een team (werk verdelen, samenwerkingsmomenten): `doc/teamwerk-mse.md`.
- Stap 0 nalopen: `doc/stap-0-controleren.md`. Slice 1 nalopen: `doc/slice-1-controleren.md`. Slice 2 nalopen: `doc/slice-2-controleren.md`. Slice 2a nalopen: `doc/slice-2a-controleren.md`.
