#!/usr/bin/env bats
# ADR 0002: de commit stage bouwt de images één keer; de acceptance stage installeert precies die images.

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

@test "install.sh --image-tag bouwt niets en installeert de gegeven images" {
  run "$SCRIPTS/install.sh" --image-tag abc1234 --skip-smoke-tests
  [ "$status" -eq 0 ]
  ! grep -q '^docker build' "$AANROEPEN"
  grep -q 'kind load docker-image --name single-node banksim-mse/bank-api:abc1234 banksim-mse/bank-web:abc1234' "$AANROEPEN"
  grep -q 'helm .*upgrade --install banksim-mse .*--set imageTag=abc1234' "$AANROEPEN"
}
