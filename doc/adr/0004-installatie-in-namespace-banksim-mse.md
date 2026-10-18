# 0004. Installatie in namespace banksim-mse van het bestaande cluster kind-single-node

Datum: 2026-10-17
Status: Geaccepteerd

## Context
Er is één lokaal kind-cluster `single-node` (OrbStack) met ingress-nginx. Het origineel installeert in namespace `banksim` met de hosts `bank.localtest.me` en `auth.localtest.me`. Beide installaties moeten naast elkaar kunnen draaien, en BankSim-MSE mag niets buiten zijn eigen namespace wijzigen.

## Beslissing
- Alle resources in namespace **`banksim-mse`**, Helm-release `banksim-mse`.
- Geen cluster-brede resources (CRD's, ClusterRoles, IngressClasses, operators) en geen wijzigingen in `ingress-nginx`.
- Eigen hosts **`bank-mse.localtest.me`** en **`auth-mse.localtest.me`**, eigen image-namen (`banksim-mse/...`) en later een eigen CA.
- De scripts gebruiken altijd context `kind-single-node`, nooit de actieve kubectl-context. Alleen `--context` wijkt af (de CI).

## Gevolgen
- Het origineel en BankSim-MSE botsen niet op hostnamen, images of resources.
- `uninstall.sh` verwijdert alleen namespace `banksim-mse`.
- Beide installaties delen de capaciteit van de node (ongeveer 12 GB).

## Bewaking
- `scripts/iac-check.sh`: elke gerenderde resource heeft namespace `banksim-mse` en er zijn geen cluster-brede resources; helm-unittest `tests/namespace_test.yaml` en `tests/ingress_test.yaml`.
- `deploy/scripts/test/context.bats`: de scripts gebruiken `kind-single-node`, ook als een andere context actief is, en weigeren een onbekende context.
