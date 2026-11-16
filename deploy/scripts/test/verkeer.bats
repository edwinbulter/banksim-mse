#!/usr/bin/env bats
# ADR 0055: verkeer.sh start k6 vanaf deze machine, via de voordeur, met de verkeersklanten uit het Secret bank-verkeer.

setup() {
  SCRIPTS="$BATS_TEST_DIRNAME/.."
  export AANROEPEN="$BATS_TEST_TMPDIR/aanroepen"
  export K6_EXIT=0
  NEP="$BATS_TEST_TMPDIR/bin"
  mkdir -p "$NEP"
  cat >"$NEP/kubectl" <<'NEP'
#!/usr/bin/env bash
echo "kubectl $*" >>"$AANROEPEN"
case "$*" in
  "config get-contexts -o name") echo kind-single-node; echo kind-banksim-mse-ci ;;
  *"get secret bank-verkeer"*) printf 'Z2VoZWlt' ;;
  *"port-forward"*) echo "Forwarding from 127.0.0.1:43181 -> 4318"; sleep 30 ;;
esac
exit 0
NEP
  cat >"$NEP/docker" <<'NEP'
#!/usr/bin/env bash
echo "docker $* WACHTWOORD=${VERKEER_WACHTWOORD:-}" >>"$AANROEPEN"
exit "$K6_EXIT"
NEP
  chmod +x "$NEP"/*
  export PATH="$NEP:$PATH"
}

@test "verkeer.sh start k6 op digest, via de voordeur, met het script uit deploy/verkeer" {
  run "$SCRIPTS/verkeer.sh" --snelheid 5 --duur 30m
  [ "$status" -eq 0 ]
  grep -q 'docker run --rm --network host .*-v .*deploy/verkeer:/verkeer:ro .*grafana/k6:2.4.0@sha256:3d8e5c7f1d53ec59408c2ffbd9943602a0985ffcbd4a2430ec00f3ffca89796e run' "$AANROEPEN"
  grep -q -- '-e SNELHEID=5 .*-e DUUR=30m' "$AANROEPEN"
  grep -q '/verkeer/verkeer.js' "$AANROEPEN"
}

@test "verkeer.sh geeft het wachtwoord via de omgeving door, niet op de opdrachtregel" {
  run "$SCRIPTS/verkeer.sh"
  [ "$status" -eq 0 ]
  grep -q -- '-e VERKEER_WACHTWOORD .*WACHTWOORD=geheim$' "$AANROEPEN"
  ! grep -q 'VERKEER_WACHTWOORD=geheim' "$AANROEPEN"
}

@test "verkeer.sh stuurt de cijfers van k6 via een port-forward naar otel-lgtm" {
  run "$SCRIPTS/verkeer.sh"
  [ "$status" -eq 0 ]
  grep -q 'kubectl --context kind-single-node -n banksim-mse port-forward svc/otel-lgtm :4318' "$AANROEPEN"
  grep -q -- '-e K6_OTEL_HTTP_EXPORTER_ENDPOINT=localhost:43181 .*--out opentelemetry' "$AANROEPEN"
}

@test "verkeer.sh gebruikt de gegeven context" {
  run "$SCRIPTS/verkeer.sh" --context kind-banksim-mse-ci
  [ "$status" -eq 0 ]
  grep -q 'kubectl --context kind-banksim-mse-ci -n banksim-mse get secret bank-verkeer' "$AANROEPEN"
}

@test "verkeer.sh faalt als k6 faalt, bijvoorbeeld door te veel fouten" {
  K6_EXIT=99 run "$SCRIPTS/verkeer.sh"
  [ "$status" -eq 99 ]
}
