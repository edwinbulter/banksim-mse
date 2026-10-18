# 0002. Eén deployment pipeline met kind als doelomgeving

Datum: 2026-10-17
Status: Geaccepteerd

## Context
Snelle, betrouwbare feedback is de basis van de werkwijze. Het origineel draait in kind (OrbStack) en heeft een CI-workflow in GitHub Actions. Een GitHub-runner kan niet bij het lokale cluster.

## Beslissing
- GitHub Actions met een **commit stage** (backend, frontend en infrastructuur, zonder cluster) en een **acceptance stage** (installatie in een tijdelijk kind-cluster `banksim-mse-ci` dat `single-node` nabootst, daarna de Playwright-acceptatietests).
- Lokaal en in CI hetzelfde `deploy/scripts/install.sh` en dezelfde Helm-chart; alleen de context verschilt.
- **Eén keer bouwen:** de commit stage bouwt de images (`deploy/scripts/build-images.sh`: Jib voor `bank-api`, Docker voor `bank-web`) met de commit als tag en bewaart ze als artefact. De acceptance stage laadt precies die images (`install.sh --image-tag`) en bouwt niets. Lokaal bouwt `install.sh` zelf, met dezelfde `build-images.sh`.
- Images gaan met `kind load` het cluster in; er is geen registry.

## Gevolgen
- Elke commit op `main` wordt automatisch gebouwd, geïnstalleerd en getest.
- De acceptance stage duurt langer dan de commit stage; daarom draait hij pas als de commit stage groen is.
- Een rode pipeline heeft voorrang op al het andere werk.

## Bewaking
- `.github/workflows/ci.yml`, gecontroleerd door `actionlint` in `scripts/iac-check.sh`.
- `deploy/scripts/test/image-tag.bats`: met `--image-tag` bouwt `install.sh` niets en installeert hij precies de gegeven images.
- De pipeline zelf: één keer opzettelijk rood maken (een unittest en het scenario) en zien dat er niet wordt gedeployed.
