#!/usr/bin/env bats
# ADR 0009: wachtwoorden en client secrets staan niet in Git; install.sh maakt ze één keer aan, in banksim-mse.

setup() {
  SCRIPTS="$BATS_TEST_DIRNAME/.."
  export AANROEPEN="$BATS_TEST_TMPDIR/aanroepen"
  export INVOER="$BATS_TEST_TMPDIR/invoer"
  export BESTAANDE_SECRETS=""
  NEP="$BATS_TEST_TMPDIR/bin"
  mkdir -p "$NEP"
  cat >"$NEP/kubectl" <<'NEP'
#!/usr/bin/env bash
echo "kubectl $*" >>"$AANROEPEN"
regel="$*"
case "$regel" in
  "config get-contexts -o name") echo kind-single-node ;;
  *" get secret "*)
    naam="${regel##* get secret }"; naam="${naam%% *}"
    [[ " $BESTAANDE_SECRETS " == *" $naam "* ]] || exit 1 ;;
  *" create secret generic "*)
    # Alleen de sleutels bewaren, niet de waarden.
    naam="${regel##* create secret generic }"; naam="${naam%% *}"
    while IFS='=' read -r sleutel waarde; do
      [[ -n "$waarde" ]] && echo "$naam $sleutel" >>"$INVOER"
    done ;;
esac
exit 0
NEP
  for cmd in helm docker kind git curl; do
    printf '#!/usr/bin/env bash\necho "%s $*" >>"$AANROEPEN"\n' "$cmd" >"$NEP/$cmd"
  done
  chmod +x "$NEP"/*
  export PATH="$NEP:$PATH"
}

@test "install.sh maakt de ontbrekende Secrets aan, met een waarde voor elke sleutel" {
  run "$SCRIPTS/install.sh" --image-tag abc1234 --skip-smoke-tests
  [ "$status" -eq 0 ]
  diff <(sort "$INVOER") - <<'VERWACHT'
bank-api SPRING_DATASOURCE_PASSWORD
bank-bff BANKSIM_BFF_CLIENT_SECRET
bank-seed BANKSIM_SEED_CLIENT_SECRET
bank-seed SPRING_DATASOURCE_PASSWORD
keycloak KC_BOOTSTRAP_ADMIN_PASSWORD
keycloak KC_DB_PASSWORD
postgres POSTGRES_PASSWORD
VERWACHT
}

@test "install.sh zet de waarden niet op de opdrachtregel en alles in namespace banksim-mse" {
  run "$SCRIPTS/install.sh" --image-tag abc1234 --skip-smoke-tests
  [ "$status" -eq 0 ]
  grep 'create secret generic' "$AANROEPEN" >"$BATS_TEST_TMPDIR/creates"
  [ "$(wc -l <"$BATS_TEST_TMPDIR/creates")" -eq 5 ]
  ! grep -q -- '--from-literal' "$BATS_TEST_TMPDIR/creates"
  ! grep -v -- '--context kind-single-node -n banksim-mse create secret generic' "$BATS_TEST_TMPDIR/creates"
}

@test "install.sh laat bestaande Secrets ongewijzigd" {
  export BESTAANDE_SECRETS="postgres bank-api bank-bff bank-seed keycloak"
  run "$SCRIPTS/install.sh" --image-tag abc1234 --skip-smoke-tests
  [ "$status" -eq 0 ]
  ! grep -qE 'secret generic|patch secret|delete secret|apply .*secret' "$AANROEPEN"
}

@test "install.sh maakt de Secrets aan vóór de Helm-installatie" {
  run "$SCRIPTS/install.sh" --image-tag abc1234 --skip-smoke-tests
  [ "$status" -eq 0 ]
  laatste_secret="$(grep -n 'create secret generic' "$AANROEPEN" | tail -1 | cut -d: -f1)"
  helm_regel="$(grep -n '^helm .*upgrade --install' "$AANROEPEN" | cut -d: -f1)"
  [ "$laatste_secret" -lt "$helm_regel" ]
}
