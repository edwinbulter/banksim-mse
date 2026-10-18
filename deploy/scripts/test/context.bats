#!/usr/bin/env bats
# ADR 0004: de scripts werken alleen op kind-single-node, tenzij je expliciet --context meegeeft.
# kubectl, helm en de andere commando's zijn nepversies die hun aanroepen vastleggen.

setup() {
  SCRIPTS="$BATS_TEST_DIRNAME/.."
  export AANROEPEN="$BATS_TEST_TMPDIR/aanroepen"
  NEP="$BATS_TEST_TMPDIR/bin"
  mkdir -p "$NEP"
  cat >"$NEP/kubectl" <<'NEP'
#!/usr/bin/env bash
echo "kubectl $*" >>"$AANROEPEN"
case "$*" in
  "config get-contexts -o name") printf '%s\n' kind-single-node kind-iets-anders ;;
  "config current-context") echo kind-iets-anders ;;
  *"get deployment ingress-nginx-controller"*) exit "${NEP_INGRESS_EXIT:-0}" ;;
esac
NEP
  for cmd in helm docker kind git curl; do
    printf '#!/usr/bin/env bash\necho "%s $*" >>"$AANROEPEN"\n' "$cmd" >"$NEP/$cmd"
  done
  chmod +x "$NEP"/*
  export PATH="$NEP:$PATH"
}

@test "install.sh weigert een context die niet bestaat" {
  run "$SCRIPTS/install.sh" --context bestaat-niet
  [ "$status" -ne 0 ]
  [[ "$output" == *"kubectl-context 'bestaat-niet' bestaat niet"* ]]
  ! grep -q '^helm ' "$AANROEPEN"
}

@test "install.sh gebruikt kind-single-node, ook als een andere context actief is" {
  # Stop na de controles: zonder ingress-nginx gaat de installatie niet verder.
  export NEP_INGRESS_EXIT=1
  run "$SCRIPTS/install.sh"
  [ "$status" -ne 0 ]
  [[ "$output" == *"ingress-nginx ontbreekt"* ]]
  grep -q -- '--context kind-single-node' "$AANROEPEN"
  ! grep -q -- 'kind-iets-anders' <(grep -v 'config get-contexts' "$AANROEPEN")
}

@test "uninstall.sh weigert een context die niet bestaat" {
  run "$SCRIPTS/uninstall.sh" --context bestaat-niet -y
  [ "$status" -ne 0 ]
  [[ "$output" == *"kubectl-context 'bestaat-niet' bestaat niet"* ]]
  ! grep -q '^helm ' "$AANROEPEN"
}
