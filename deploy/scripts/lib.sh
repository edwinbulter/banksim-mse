# Gedeelde functies voor de BankSim-MSE-scripts. Wordt gesourced, niet uitgevoerd.
# shellcheck shell=bash
# shellcheck disable=SC2034  # De variabelen gebruiken de scripts die lib.sh sourcen.

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
NAMESPACE="banksim-mse"
RELEASE="banksim-mse"
# ADR 0004: altijd het bestaande cluster single-node, nooit de actieve kubectl-context. Alleen --context wijkt af.
KUBE_CONTEXT="kind-single-node"

info() { printf '\033[1;34m==>\033[0m %s\n' "$*"; }
ok()   { printf '\033[1;32m ✓\033[0m %s\n' "$*"; }
warn() { printf '\033[1;33m !\033[0m %s\n' "$*" >&2; }
fail() { printf '\033[1;31m ✗\033[0m %s\n' "$*" >&2; exit 1; }

require() {
  local cmd
  for cmd in "$@"; do
    command -v "$cmd" >/dev/null || fail "Vereist commando ontbreekt: $cmd"
  done
}

kc() { kubectl --context "$KUBE_CONTEXT" "$@"; }

# Naam van het kind-cluster bij de context (kind-single-node → single-node).
kind_cluster_name() { printf '%s' "${KUBE_CONTEXT#kind-}"; }

check_context() {
  # Eerst de hele lijst ophalen: met "| grep -q" kan kubectl onder pipefail een SIGPIPE-fout geven.
  local contexten
  contexten="$(kubectl config get-contexts -o name)"
  grep -qx "$KUBE_CONTEXT" <<<"$contexten" \
    || fail "kubectl-context '$KUBE_CONTEXT' bestaat niet (kies een andere met --context)"
  kc get --raw /readyz >/dev/null 2>&1 || fail "Cluster van context '$KUBE_CONTEXT' is niet bereikbaar"
}
