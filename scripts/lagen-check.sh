#!/usr/bin/env bash
# Het vierlaagse model (skill atdd §3, ADR 0031): een spec spreekt alleen de DSL. Hij importeert alleen uit dsl/ en
# gebruikt geen page, request of expect: hoe het systeem wordt aangestuurd en wat er beweerd wordt, staat in de DSL
# en de drivers.
#
#   scripts/lagen-check.sh [map met specs]    (standaard e2e/specs)
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SPECS="${1:-$REPO_ROOT/e2e/specs}"

specs=()
if [[ -d "$SPECS" ]]; then
  while IFS= read -r -d '' spec; do specs+=("$spec"); done < <(find "$SPECS" -name '*.spec.ts' -print0)
fi
if [[ ${#specs[@]} -eq 0 ]]; then
  printf 'Vierlaagse model: geen specs gevonden in %s\n' "$SPECS" >&2
  exit 1
fi

fouten=0
meld() {
  printf '%s:%s: %s: %s\n' "${1#"$SPECS"/}" "$2" "$3" "$4" >&2
  fouten=1
}

for spec in "${specs[@]}"; do
  # Elke bron van een import, ook de laatste regel van een import over meerdere regels.
  while IFS=: read -r nummer regel; do
    meld "$spec" "$nummer" "importeert buiten de DSL" "$regel"
  done < <(grep -nE "(from|^import) '[^']+'" "$spec" | grep -vE "'(\.\./)+dsl/[^']+'" || true)
  while IFS=: read -r nummer regel; do
    meld "$spec" "$nummer" "spreekt het systeem of Playwright direct aan" "$regel"
  done < <(grep -nE "(^|[^[:alnum:]_.])((page|request)\.|expect\()" "$spec" || true)
done

if [[ $fouten -ne 0 ]]; then
  printf 'Een spec spreekt alleen de DSL (skill atdd §3, ADR 0031).\n' >&2
  exit 1
fi
