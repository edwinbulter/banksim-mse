#!/usr/bin/env bash
# Installeert BankSim-MSE in namespace banksim-mse van het kind-cluster single-node (ADR 0004).
# Idempotent: opnieuw draaien werkt de installatie bij.
#
#   install.sh [--context <kubectl-context>] [--image-tag <tag>] [--skip-build] [--skip-smoke-tests]
#     --image-tag  niet bouwen, maar de al gebouwde images met deze tag laden (de acceptance stage in CI)
#     --skip-build niet bouwen en niet laden; de tag van de bestaande installatie opnieuw gebruiken
set -euo pipefail
# shellcheck source-path=SCRIPTDIR
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

skip_build=false
image_tag_arg=""
smoke_tests=true
while [[ $# -gt 0 ]]; do
  case "$1" in
    --context) KUBE_CONTEXT="$2"; shift ;;
    --image-tag) image_tag_arg="$2"; shift ;;
    --skip-build) skip_build=true ;;
    --skip-smoke-tests) smoke_tests=false ;;
    -h|--help) sed -n '2,7p' "$0"; exit 0 ;;
    *) fail "Onbekende optie: $1" ;;
  esac
  shift
done

BANK_URL="https://bank-mse.localtest.me"
AUTH_URL="https://auth-mse.localtest.me"

check_prerequisites() {
  info "Vereisten controleren"
  require kubectl helm docker kind git curl
  check_context
  kc -n ingress-nginx get deployment ingress-nginx-controller >/dev/null 2>&1 \
    || fail "ingress-nginx ontbreekt in het cluster (namespace ingress-nginx)"
  ok "Context $KUBE_CONTEXT, ingress-nginx aanwezig"
}

image_tag() {
  local tag
  tag="$(git -C "$REPO_ROOT" rev-parse --short HEAD 2>/dev/null || echo dev)"
  if [[ -n "$(git -C "$REPO_ROOT" status --porcelain -- backend frontend 2>/dev/null)" ]]; then
    tag="$tag-dirty-$(date +%s)"
  fi
  printf '%s' "$tag"
}

load_images() {
  local tag="$1"
  info "Images laden in kind-cluster $(kind_cluster_name)"
  # bank-seed draait niet als Deployment; de DSL start hem per scenario (ADR 0003).
  kind load docker-image --name "$(kind_cluster_name)" \
    "banksim-mse/bank-api:$tag" "banksim-mse/bank-bff:$tag" "banksim-mse/bank-seed:$tag" "banksim-mse/bank-web:$tag" \
    >/dev/null
  ok "Images geladen"
}

create_namespace() {
  info "Namespace $NAMESPACE"
  kc apply -f - >/dev/null <<YAML
apiVersion: v1
kind: Namespace
metadata:
  name: $NAMESPACE
  labels:
    app.kubernetes.io/part-of: banksim-mse
YAML
  ok "Namespace aanwezig"
}

random_secret() { head -c 24 /dev/urandom | od -An -tx1 | tr -d ' \n'; }

# Maakt een Secret met een willekeurige waarde per sleutel, alleen als het nog niet bestaat (ADR 0009).
# De waarden gaan via stdin, niet via de opdrachtregel. Een bestaand Secret blijft ongewijzigd.
ensure_secret() {
  local naam="$1" sleutel
  shift
  kc -n "$NAMESPACE" get secret "$naam" >/dev/null 2>&1 && return 0
  for sleutel in "$@"; do
    printf '%s=%s\n' "$sleutel" "$(random_secret)"
  done | kc -n "$NAMESPACE" create secret generic "$naam" --from-env-file=/dev/stdin >/dev/null
  kc -n "$NAMESPACE" label secret "$naam" app.kubernetes.io/part-of=banksim-mse \
    "app.kubernetes.io/instance=$RELEASE" >/dev/null
  ok "Secret $naam aangemaakt"
}

create_secrets() {
  info "Wachtwoorden en client secrets"
  ensure_secret postgres POSTGRES_PASSWORD
  ensure_secret bank-api SPRING_DATASOURCE_PASSWORD
  ensure_secret bank-seed SPRING_DATASOURCE_PASSWORD BANKSIM_SEED_CLIENT_SECRET
  ensure_secret bank-bff BANKSIM_BFF_CLIENT_SECRET
  ensure_secret keycloak KC_DB_PASSWORD KC_BOOTSTRAP_ADMIN_PASSWORD
  ok "Secrets aanwezig"
}

helm_install() {
  local tag="$1"
  info "Helm-release $RELEASE installeren"
  helm --kube-context "$KUBE_CONTEXT" upgrade --install "$RELEASE" "$REPO_ROOT/deploy/banksim-mse" \
    --namespace "$NAMESPACE" \
    --set imageTag="$tag" \
    --wait --timeout 10m >/dev/null
  ok "Release $RELEASE geïnstalleerd (image-tag $tag)"
}

expect_status() {
  local url="$1" expected="$2" actual
  # ingress-nginx heeft na een nieuwe Ingress een paar seconden nodig om hem op te pakken.
  for _ in {1..10}; do
    actual="$(curl -sk -o /dev/null -w '%{http_code}' --max-time 10 "$url" || true)"
    [[ "$actual" == "$expected" ]] && { ok "$url → $expected"; return 0; }
    sleep 2
  done
  fail "$url gaf $actual, verwacht $expected"
}

smoke_tests() {
  info "Rooktests"
  expect_status "$BANK_URL/" 200
  expect_status "$BANK_URL/api/versie" 200
  expect_status "$AUTH_URL/realms/banksim-mse/.well-known/openid-configuration" 200
}

main() {
  check_prerequisites
  local tag
  if [[ -n "$image_tag_arg" ]]; then
    tag="$image_tag_arg"
    load_images "$tag"
  elif [[ "$skip_build" == true ]]; then
    tag="$(helm --kube-context "$KUBE_CONTEXT" -n "$NAMESPACE" get values "$RELEASE" -o json 2>/dev/null \
      | sed -n 's/.*"imageTag":"\([^"]*\)".*/\1/p' || true)"
    [[ -n "$tag" ]] || fail "--skip-build vereist een bestaande installatie"
  else
    tag="$(image_tag)"
    "$REPO_ROOT/deploy/scripts/build-images.sh" "$tag"
    load_images "$tag"
  fi
  create_namespace
  create_secrets
  helm_install "$tag"
  if [[ "$smoke_tests" == true ]]; then
    smoke_tests
  fi
  printf '\nBankSim-MSE draait op %s (namespace %s, context %s).\n' "$BANK_URL" "$NAMESPACE" "$KUBE_CONTEXT"
}

main
