# Stack: infrastructuur als code met Helm, shellscripts en kind

Ook infrastructuur doorloopt de double loop.
- **Buitenste lus:** een installatiescenario of een acceptatietest tegen de geïnstalleerde omgeving.
- **Binnenste lus:** tests die geen cluster nodig hebben. Die zijn snel en horen in de commit stage.

| Wat | Binnenste lus (zonder cluster) | Buitenste lus (met cluster) |
| --- | --- | --- |
| Helm-chart | helm-unittest, `helm lint`, kubeconform, een check op `helm template` met yq | Installatiescenario |
| Shellscripts | bats met nepcommando's, shellcheck | Echte installatie, met rooktests |
| CI-workflow | actionlint | De pipeline zelf: één keer rood zien |
| Dockerfile | hadolint | De image draait in de acceptance stage |

## Tools in containers, vastgepind op digest

Draai de tools in containers met een vaste versie, lokaal en in CI hetzelfde. Voor de tools is dan alleen Docker nodig.

```bash
# Als de eigen gebruiker: in CI is de checkout van uid 1001 en de images draaien als een andere gebruiker.
in_repo() { docker run --rm -i --user "$(id -u):$(id -g)" -e HOME=/tmp -v "$REPO_ROOT:/repo" -w /repo "$@"; }

in_repo --platform linux/amd64 "helmunittest/helm-unittest:<versie>@sha256:<digest>" "$CHART"
helm template "$RELEASE" "$CHART" --namespace "$NAMESPACE" \
  | in_repo "ghcr.io/yannh/kubeconform:<versie>@sha256:<digest>" -strict -summary -kubernetes-version "$K8S" -
in_repo "koalaman/shellcheck:<versie>@sha256:<digest>" -x scripts/*.sh
in_repo "bats/bats:<versie>@sha256:<digest>" scripts/test
in_repo "rhysd/actionlint:<versie>@sha256:<digest>"
```

## helm-unittest

```yaml
suite: alles in de eigen namespace
release:
  name: mijn-app
  namespace: mijn-app
templates:
  - templates/api.yaml
  - templates/ingress.yaml
tests:
  - it: zet de namespace van de release op elke resource
    asserts:
      - equal:
          path: metadata.namespace
          value: mijn-app
```

Let op: een suite test alleen de templates die je bij `templates:` opsomt. Een nieuw templatebestand valt daar dus buiten. Vang dat op met een check op de complete `helm template`-output:

```bash
printf '%s\n' "$manifests" | in_repo "mikefarah/yq:<versie>@sha256:<digest>" -N e '
  select(.kind != null)
  | select(.metadata.namespace != "mijn-app"
      or (.kind | test("^(Namespace|Cluster.*|CustomResourceDefinition|IngressClass|PersistentVolume|StorageClass|PriorityClass|RuntimeClass|.*WebhookConfiguration|APIService)$")))
  | .kind + "/" + .metadata.name' -
# Lege output = goed; anders falen met de gevonden resources.
```

## bats met nepcommando's

```bash
setup() {
  export AANROEPEN="$BATS_TEST_TMPDIR/aanroepen"
  NEP="$BATS_TEST_TMPDIR/bin"; mkdir -p "$NEP"
  cat >"$NEP/kubectl" <<'NEP'
#!/usr/bin/env bash
echo "kubectl $*" >>"$AANROEPEN"
case "$*" in
  "config get-contexts -o name") printf '%s\n' kind-mijn-cluster kind-iets-anders ;;
  "config current-context") echo kind-iets-anders ;;
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
  [[ "$output" == *"bestaat niet"* ]]
  ! grep -q '^helm ' "$AANROEPEN"
}
```

## Patronen voor scripts

- **Vaste doelcontext**, nooit de actieve kubectl-context. Alleen een expliciete `--context` mag afwijken. Test dat met bats.
- **Eén keer bouwen.** `install.sh --image-tag <tag>` laadt alleen bestaande images, zodat CI precies de artefacten uit de commit stage installeert. Lokaal bouwt het script zelf, met hetzelfde bouwscript.
- **Unieke image-tag per build:** de korte commit-hash, plus `-dirty-<tijdstempel>` als er niet-gecommitte wijzigingen zijn. Toon die tag in de app, zodat je kunt zien welke build er draait.
- **Idempotent.** Gebruik `helm upgrade --install`. Opnieuw installeren mag niets veranderen.
- **Uninstall laat niets achter** buiten de eigen namespace. Controleer dat met een label, bijvoorbeeld `app.kubernetes.io/instance`.

## Valkuilen (echt opgetreden)

- **helm-unittest-image:**
  - Het image heeft de plugin alleen voor amd64. Op arm64 draai je het met `--platform linux/amd64`. Geeft Docker daarna "cannot overwrite digest", verwijder dan eerst het lokale image.
  - Het image draait als uid 1000. In GitHub Actions is de checkout van uid 1001, dus dan volgt `mkdir tests/__snapshot__: permission denied`. Draai de container met `--user "$(id -u):$(id -g)" -e HOME=/tmp`.
  - Op macOS met OrbStack of Docker Desktop zie je beide problemen niet.
- **yq over meerdere documenten:** gebruik `-N e`, niet `ea`. `ea` geeft hier een cartesisch product over de documenten.
- **shellcheck:**
  - Bij `source "$(dirname …)/lib.sh"` hoort `# shellcheck source-path=SCRIPTDIR`, anders volgt SC1091.
  - Variabelen in een gesourcede lib geven SC2034. Zet `# shellcheck disable=SC2034` in de lib, met uitleg.
- **ingress-nginx in een vers kind-cluster:** `kubectl wait … available` is niet genoeg. De admission-webhook kan daarna nog "connection refused" geven, waardoor `helm install` faalt. Wacht tot een server-side dry-run lukt:
  ```bash
  for _ in $(seq 30); do
    kubectl create ingress probe -n default --class=nginx --rule='probe.local/=probe:80' \
      --dry-run=server -o name >/dev/null 2>&1 && exit 0
    sleep 2
  done
  exit 1
  ```
- **Nieuwe Ingress:** ingress-nginx heeft een paar seconden nodig om hem op te pakken. Laat rooktests een paar keer opnieuw proberen.
- **kind en CI:** een GitHub-runner kan niet bij je lokale cluster. Maak in de acceptance stage een tijdelijk kind-cluster dat het lokale nabootst: dezelfde node-image, dezelfde ingress-versie en poorten 80/443. Installeer met hetzelfde script en een andere `--context`. Een host als `*.localtest.me` wijst naar 127.0.0.1, dus de tests op de runner bereiken de ingress.
- **Runner-image:** zet `runs-on` vast op een versie, bijvoorbeeld `ubuntu-24.04`. `ubuntu-latest` verschuift vanzelf naar een nieuwe versie.
