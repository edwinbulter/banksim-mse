#!/usr/bin/env bats
# ADR 0054: otel-lgtm staat standaard aan; CI zet het uit in de jobs die de telemetrie niet testen.
# ADR 0055: de verkeersklanten voor k6 staan standaard uit.

setup() {
  SCRIPTS="$BATS_TEST_DIRNAME/.."
  export AANROEPEN="$BATS_TEST_TMPDIR/aanroepen"
  NEP="$BATS_TEST_TMPDIR/bin"
  mkdir -p "$NEP"
  cat >"$NEP/kubectl" <<'NEP'
#!/usr/bin/env bash
echo "kubectl $*" >>"$AANROEPEN"
[[ "$*" == "config get-contexts -o name" ]] && echo kind-single-node
exit 0
NEP
  for cmd in helm docker kind git curl; do
    printf '#!/usr/bin/env bash\necho "%s $*" >>"$AANROEPEN"\n' "$cmd" >"$NEP/$cmd"
  done
  chmod +x "$NEP"/*
  export PATH="$NEP:$PATH"
}

@test "install.sh installeert standaard met otel-lgtm" {
  run "$SCRIPTS/install.sh" --image-tag abc1234 --skip-smoke-tests
  [ "$status" -eq 0 ]
  grep -q 'helm .*upgrade --install banksim-mse .*--set observability.enabled=true' "$AANROEPEN"
}

@test "install.sh installeert zonder otel-lgtm met BANKSIM_OBSERVABILITY=false" {
  BANKSIM_OBSERVABILITY=false run "$SCRIPTS/install.sh" --image-tag abc1234 --skip-smoke-tests
  [ "$status" -eq 0 ]
  grep -q 'helm .*upgrade --install banksim-mse .*--set observability.enabled=false' "$AANROEPEN"
}

@test "install.sh weigert een andere waarde voor BANKSIM_OBSERVABILITY" {
  BANKSIM_OBSERVABILITY=nee run "$SCRIPTS/install.sh" --image-tag abc1234 --skip-smoke-tests
  [ "$status" -ne 0 ]
  [[ "$output" == *"BANKSIM_OBSERVABILITY"* ]]
}

@test "install.sh installeert standaard zonder verkeersklanten, en met BANKSIM_VERKEER=true met (ADR 0055)" {
  run "$SCRIPTS/install.sh" --image-tag abc1234 --skip-smoke-tests
  grep -q 'helm .*--set verkeer.klanten=false' "$AANROEPEN"
  : >"$AANROEPEN"
  BANKSIM_VERKEER=true run "$SCRIPTS/install.sh" --image-tag abc1234 --skip-smoke-tests
  [ "$status" -eq 0 ]
  grep -q 'helm .*--set verkeer.klanten=true' "$AANROEPEN"
}
