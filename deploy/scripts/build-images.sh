#!/usr/bin/env bash
# Bouwt de images van BankSim-MSE met de gegeven tag: bank-api, bank-bff en bank-seed met Jib, bank-web met Docker.
# install.sh gebruikt dit lokaal; in CI bouwt de commit stage de images één keer (ADR 0002).
#
#   build-images.sh <tag> [--platform linux/amd64|linux/arm64]
set -euo pipefail
# shellcheck source-path=SCRIPTDIR
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

[[ $# -ge 1 ]] || fail "Gebruik: build-images.sh <tag> [--platform <platform>]"
tag="$1"
shift
platform=""
while [[ $# -gt 0 ]]; do
  case "$1" in
    --platform) platform="$2"; shift ;;
    *) fail "Onbekende optie: $1" ;;
  esac
  shift
done

require docker
if [[ -z "$platform" ]]; then
  case "$(docker info --format '{{.Architecture}}')" in
    aarch64|arm64) platform="linux/arm64" ;;
    *) platform="linux/amd64" ;;
  esac
fi

info "Images bouwen ($tag, $platform)"
"$REPO_ROOT/backend/mvnw" -q -B -ntp -f "$REPO_ROOT/backend/pom.xml" \
  -pl bank-api,bank-bff,bank-seed -am package jib:dockerBuild \
  -DskipTests -Dimage.tag="$tag" -Djib.from.platforms="$platform"
docker build -q --platform "$platform" -t "banksim-mse/bank-web:$tag" "$REPO_ROOT/frontend" >/dev/null
ok "Images gebouwd: banksim-mse/{bank-api,bank-bff,bank-seed,bank-web}:$tag"
