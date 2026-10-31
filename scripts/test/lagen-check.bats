#!/usr/bin/env bats
# Het vierlaagse model (skill atdd §3, ADR 0031): een spec spreekt alleen de DSL.

setup() {
  CHECK="$BATS_TEST_DIRNAME/../lagen-check.sh"
  SPECS="$BATS_TEST_TMPDIR/specs"
  mkdir -p "$SPECS/infra"
}

goede_spec() {
  cat >"$SPECS/$1" <<'SPEC'
import { test } from '../dsl/fixtures';
import type { Boeking } from '../dsl/klant';

test('Betalen', async ({ bank }) => {
  await bank.dan('is het saldo € 475,00', () => bank.klant('Jan').verwachtSaldo('475.00'));
});
SPEC
}

@test "specs die alleen de DSL gebruiken zijn goed" {
  goede_spec betalen.spec.ts
  printf "import { test } from '../../dsl/fixtures';\n" >"$SPECS/infra/installatie.spec.ts"
  run "$CHECK" "$SPECS"
  [ "$status" -eq 0 ]
}

@test "een import van een driver wordt gemeld" {
  goede_spec betalen.spec.ts
  printf "import { RestDriver } from '../drivers/rest/rest-driver';\n" >>"$SPECS/betalen.spec.ts"
  run "$CHECK" "$SPECS"
  [ "$status" -ne 0 ]
  [[ "$output" == *"betalen.spec.ts:7: importeert buiten de DSL"* ]]
}

@test "een import van Playwright over meerdere regels wordt gemeld" {
  goede_spec betalen.spec.ts
  printf "import {\n  expect,\n} from '@playwright/test';\n" >>"$SPECS/betalen.spec.ts"
  run "$CHECK" "$SPECS"
  [ "$status" -ne 0 ]
  [[ "$output" == *"importeert buiten de DSL: } from '@playwright/test';"* ]]
}

@test "page, request en expect in een spec worden gemeld" {
  goede_spec betalen.spec.ts
  printf "await page.goto('/');\nawait request.get('/api/versie');\nexpect(1).toBe(1);\n" >>"$SPECS/betalen.spec.ts"
  run "$CHECK" "$SPECS"
  [ "$status" -ne 0 ]
  [[ "$output" == *"betalen.spec.ts:7: spreekt het systeem of Playwright direct aan"* ]]
  [[ "$output" == *"betalen.spec.ts:8: spreekt het systeem of Playwright direct aan"* ]]
  [[ "$output" == *"betalen.spec.ts:9: spreekt het systeem of Playwright direct aan"* ]]
}

@test "een woord dat op page eindigt is geen page" {
  goede_spec betalen.spec.ts
  printf "await bank.klant('Jan').opentHomepage.nu();\n" >>"$SPECS/betalen.spec.ts"
  run "$CHECK" "$SPECS"
  [ "$status" -eq 0 ]
}

@test "zonder specs faalt de controle, zodat een verkeerde map niet stil groen is" {
  run "$CHECK" "$BATS_TEST_TMPDIR/leeg"
  [ "$status" -ne 0 ]
  [[ "$output" == *"geen specs gevonden"* ]]
}

@test "de specs in e2e/specs voldoen" {
  run "$CHECK"
  [ "$status" -eq 0 ]
}
