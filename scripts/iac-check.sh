#!/usr/bin/env bash
# Binnenste lus voor de infrastructuur (doc/werkwijze-mse.md, hoofdstuk 9): chart, scripts en CI-workflow
# testen zonder cluster. De tools draaien in vastgepinde container-images, lokaal en in CI hetzelfde (ADR 0006).
#
#   scripts/iac-check.sh
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CHART="deploy/banksim-mse"
NAMESPACE="banksim-mse"
# Zelfde Kubernetes-versie als kind-single-node.
KUBERNETES_VERSION="1.35.0"

HELM_UNITTEST="helmunittest/helm-unittest:4.2.4-1.2.1@sha256:8f80633476c2554b1c58c3f499d16fd3aebd13391dafa3d984f6e4643d76cd1f"
KUBECONFORM="ghcr.io/yannh/kubeconform:v0.8.0@sha256:faffaf43f95aa6425306e1ab8d6fcad72acb9049158f38e574c085ea1ec0f64e"
YQ="mikefarah/yq:4.54.1@sha256:4b3d9475d65571d28cbb19544d3820ec2945e4c8b2f18279394282b8dc3a592e"
SHELLCHECK="koalaman/shellcheck:v0.11.0@sha256:61862eba1fcf09a484ebcc6feea46f1782532571a34ed51fedf90dd25f925a8d"
BATS="bats/bats:1.14.0@sha256:5322b877351fda0cc435de8c6116de7d0a2ec79d7c680132a0ef329a633bc66f"
ACTIONLINT="rhysd/actionlint:1.7.12@sha256:b1934ee5f1c509618f2508e6eb47ee0d3520686341fec936f3b79331f9315667"

stap() { printf '\033[1;34m==>\033[0m %s\n' "$*"; }
in_repo() { docker run --rm -i -v "$REPO_ROOT:/repo" -w /repo "$@"; }

cd "$REPO_ROOT"

stap "helm lint"
helm lint "$CHART" >/dev/null

stap "helm unittest"
# Het image bevat de plugin alleen voor amd64; op arm64 draait hij via emulatie.
in_repo --platform linux/amd64 "$HELM_UNITTEST" "$CHART"

manifests="$(helm template banksim-mse "$CHART" --namespace "$NAMESPACE")"

stap "Alles in namespace $NAMESPACE, geen cluster-brede resources (ADR 0004)"
afwijkend="$(printf '%s\n' "$manifests" | in_repo "$YQ" -N e '
  select(.kind != null)
  | select(
      .metadata.namespace != "'"$NAMESPACE"'"
      or (.kind | test("^(Namespace|Cluster.*|CustomResourceDefinition|IngressClass|PersistentVolume|StorageClass|PriorityClass|RuntimeClass|.*WebhookConfiguration|APIService)$")))
  | .kind + "/" + .metadata.name' -)"
if [[ -n "$afwijkend" ]]; then
  printf 'Niet toegestaan:\n%s\n' "$afwijkend" >&2
  exit 1
fi

stap "kubeconform (Kubernetes $KUBERNETES_VERSION)"
printf '%s\n' "$manifests" | in_repo "$KUBECONFORM" -strict -summary -kubernetes-version "$KUBERNETES_VERSION" -

stap "shellcheck"
in_repo "$SHELLCHECK" -x deploy/scripts/*.sh scripts/*.sh

stap "bats"
in_repo "$BATS" deploy/scripts/test

if [[ -d .github/workflows ]]; then
  stap "actionlint"
  in_repo "$ACTIONLINT"
fi

printf '\033[1;32m ✓\033[0m Alle infrastructuurchecks groen\n'
