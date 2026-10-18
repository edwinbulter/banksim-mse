# 0006. Infrastructuur testen met helm-unittest, kubeconform, bats en shellcheck in containers

Datum: 2026-10-17
Status: Geaccepteerd

## Context
De Helm-chart, de installatiescripts en de CI-workflow worden test-first gebouwd (doc/werkwijze-mse.md, hoofdstuk 9). Het origineel heeft geen gereedschap om die zonder cluster te testen. De tools staan niet op de ontwikkelmachine, en het systeem moet niet worden aangepast.

## Beslissing
- Uitbreiding van de stack met helm-unittest, kubeconform, yq, shellcheck, bats en actionlint. Later volgen conftest en hadolint (CFR-slices hardening en supply chain).
- Ze draaien in container-images, vastgepind op digest, via `scripts/iac-check.sh`. Lokaal en in CI hetzelfde.
- Het image van helm-unittest heeft de plugin alleen voor amd64; op arm64 draait het via emulatie (`--platform linux/amd64`).

## Gevolgen
- Niets te installeren behalve Docker; de versies zijn reproduceerbaar.
- De eerste run haalt de images op; daarna duurt de check ongeveer een halve minuut.
- Nieuwe versies van de images moeten met de hand (later Renovate) worden bijgewerkt, inclusief digest.

## Bewaking
- De job `infrastructuur` in de commit stage draait `scripts/iac-check.sh`.
- Afwijken van de stack zonder ADR is in strijd met de Definition of Done.
