#!/usr/bin/env bash
# Doorlopend verkeer op BankSim-MSE met k6 (ADR 0055), vanaf deze machine via de voordeur, zoals Playwright.
# Verwacht de verkeersklanten: installeer eerst met BANKSIM_VERKEER=true (make verkeer doet dat).
#
#   verkeer.sh [--context <kubectl-context>] [--snelheid <bezoeken per seconde>] [--duur <duur, zoals 30s of 10m>]
#     De cijfers van k6 gaan via een port-forward naar otel-lgtm (ADR 0054), als dat er is. Ctrl-C stopt alles.
set -euo pipefail
# shellcheck source-path=SCRIPTDIR
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

# ADR 0006: op digest vast, net als de testtools.
K6_IMAGE="grafana/k6:2.4.0@sha256:3d8e5c7f1d53ec59408c2ffbd9943602a0985ffcbd4a2430ec00f3ffca89796e"

snelheid=2
duur=10m
while [[ $# -gt 0 ]]; do
  case "$1" in
    --context) KUBE_CONTEXT="$2"; shift ;;
    --snelheid) snelheid="$2"; shift ;;
    --duur) duur="$2"; shift ;;
    -h|--help) sed -n '2,6p' "$0"; exit 0 ;;
    *) fail "Onbekende optie: $1" ;;
  esac
  shift
done

require kubectl docker
check_context

VERKEER_WACHTWOORD="$(kc -n "$NAMESPACE" get secret bank-verkeer -o jsonpath='{.data.VERKEER_WACHTWOORD}' | base64 -d)"
[[ -n "$VERKEER_WACHTWOORD" ]] || fail "Het Secret bank-verkeer ontbreekt: installeer met BANKSIM_VERKEER=true"
export VERKEER_WACHTWOORD

# Een vrije poort op deze machine naar OTLP van otel-lgtm; de uitvoer van kubectl noemt de poort.
uitvoer="$(mktemp)"
kc -n "$NAMESPACE" port-forward svc/otel-lgtm :4318 >"$uitvoer" 2>&1 &
port_forward=$!
trap 'kill "$port_forward" 2>/dev/null || true; rm -f "$uitvoer"' EXIT
poort=""
for _ in {1..20}; do
  poort="$(sed -n 's/^Forwarding from 127.0.0.1:\([0-9]*\).*/\1/p' "$uitvoer" | head -1)"
  [[ -n "$poort" ]] && break
  kill -0 "$port_forward" 2>/dev/null || break
  sleep 0.5
done
cijfers=()
if [[ -n "$poort" ]]; then
  cijfers=(-e K6_OTEL_EXPORTER_PROTOCOL=http/protobuf -e "K6_OTEL_HTTP_EXPORTER_ENDPOINT=localhost:$poort"
    -e K6_OTEL_HTTP_EXPORTER_INSECURE=true -e K6_OTEL_METRIC_PREFIX=k6_)
  ok "De cijfers van k6 gaan naar otel-lgtm"
else
  warn "Geen otel-lgtm bereikbaar: k6 draait zonder cijfers in Grafana"
fi

info "k6: $snelheid klantbezoeken per seconde, $duur"
# --network host: bank-mse.localtest.me is 127.0.0.1, en daar luistert de ingress van kind.
set +e
docker run --rm --network host -v "$REPO_ROOT/deploy/verkeer:/verkeer:ro" \
  -e "SNELHEID=$snelheid" -e "DUUR=$duur" -e VERKEER_WACHTWOORD ${cijfers[@]+"${cijfers[@]}"} \
  "$K6_IMAGE" run --quiet ${poort:+--out opentelemetry} /verkeer/verkeer.js
status=$?
set -e
exit "$status"
