#!/usr/bin/env bats
# uninstall.sh --purge: verwijdert alleen de BankSim-MSE-images van de node, elk als eigen argument.
# kubectl, helm en docker zijn nepversies; docker legt elk argument van "crictl rmi" op een eigen regel vast.

setup() {
  SCRIPTS="$BATS_TEST_DIRNAME/.."
  export AANROEPEN="$BATS_TEST_TMPDIR/aanroepen"
  NEP="$BATS_TEST_TMPDIR/bin"
  mkdir -p "$NEP"
  cat >"$NEP/kubectl" <<'NEP'
#!/usr/bin/env bash
case "$*" in
  "config get-contexts -o name") echo kind-single-node ;;
  *"get namespace"*) exit 1 ;;
esac
NEP
  cat >"$NEP/helm" <<'NEP'
#!/usr/bin/env bash
exit 1
NEP
  cat >"$NEP/docker" <<'NEP'
#!/usr/bin/env bash
case "$*" in
  "inspect single-node-control-plane") ;;
  "exec single-node-control-plane crictl images -o json")
    printf '%s\n' '{"images": [' \
      '{"repoTags": ["docker.io/banksim-mse/bank-api:abc1234"]},' \
      '{"repoTags": ["docker.io/banksim-mse/bank-web:abc1234"]},' \
      '{"repoTags": ["docker.io/library/postgres:18"]}' ']}' ;;
  "exec single-node-control-plane crictl rmi "*) shift 4; printf 'rmi %s\n' "$@" >>"$AANROEPEN" ;;
  *) echo "onverwacht: docker $*" >&2; exit 1 ;;
esac
NEP
  chmod +x "$NEP"/*
  export PATH="$NEP:$PATH"
}

@test "uninstall.sh --purge verwijdert alleen de BankSim-MSE-images, elk als eigen argument" {
  run "$SCRIPTS/uninstall.sh" -y --purge
  [ "$status" -eq 0 ]
  [ "$(cat "$AANROEPEN")" = "$(printf '%s\n' \
    'rmi docker.io/banksim-mse/bank-api:abc1234' \
    'rmi docker.io/banksim-mse/bank-web:abc1234')" ]
}
