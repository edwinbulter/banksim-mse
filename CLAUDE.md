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
- Installeren + acceptatietests: `make acceptance`, dat `make install` en daarna `make e2e` uitvoert.
- Eén scenario: `npm --prefix e2e test -- --project=rest specs/<naam>.spec.ts`. Gebruik `--project=chromium` voor de UI.
- Welke versie draait: `curl -sk https://bank-mse.localtest.me/api/versie`, of de voettekst. Dit is de image-tag: de korte commit-hash, eventueel met `-dirty-<tijdstempel>`.
- Pipeline: `gh run list --limit 3` en `gh run watch <id> --exit-status`. Bij falen: `gh api repos/edwinbulter/banksim-mse/actions/jobs/<job-id>/logs`.

**Waar komt wat**

| Wijziging | Waar | Test |
| --- | --- | --- |
| Scenario | `e2e/specs/` (infra: `e2e/specs/infra/`) | Playwright: projecten `rest`, `chromium`, `firefox`, `webkit` |
| DSL / drivers | `e2e/dsl/`, `e2e/drivers/{rest,ui}/` | Via de scenario's, plus `npm --prefix e2e run typecheck` |
| Domein | `backend/bank-domain` (zonder Spring) | JUnit, jqwik |
| API | `backend/bank-api` | MockMvc of slicetest |
| UI | `frontend/src/app` | Vitest |
| Helm-chart | `deploy/banksim-mse/templates` | `deploy/banksim-mse/tests/*_test.yaml`, plus de namespacecheck in `scripts/iac-check.sh` |
| Scripts | `deploy/scripts` | `deploy/scripts/test/*.bats`, shellcheck |
| Pipeline | `.github/workflows/ci.yml` | actionlint |
| ADR | `doc/adr/NNNN-titel.md` | – |

**Doelomgeving:** alleen kubectl-context `kind-single-node` en namespace `banksim-mse`, op host `bank-mse.localtest.me` (ADR 0004). Er komen geen cluster-brede resources. Het origineel in namespace `banksim` blijft onaangeroerd. Daarnaast is verboden:
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
- Stap 0 nalopen: `doc/stap-0-controleren.md`.
