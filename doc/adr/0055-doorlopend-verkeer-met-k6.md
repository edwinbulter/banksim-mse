# 0055. Doorlopend verkeer komt van k6, vanaf de Mac, met een kring van verkeersklanten

Datum: 2026-11-16
Status: Geaccepteerd

## Context
Om te zien wat de bank onder load doet (ADR 0054), is er verkeer nodig dat lijkt op klanten. Een fast-forward van de simulatiedatum geeft geen load, en is daarom vervallen. De transacties tot eind 2026 zijn vooraf gegenereerd; een nieuwe dag maakt ze alleen zichtbaar.

De afweging staat in [observability-opties.md](../observability-opties.md), het advies in [observability-keuzes.md](../observability-keuzes.md) (stap 5). De spike van 2026-11-15 liet drie dingen zien:
- **k6 in een pod** komt niet via de voordeur bij de bank. In een pod verwijst `bank-mse.localtest.me` naar 127.0.0.1, en dat is de pod zelf. Er zijn twee uitwegen:
  - `hostAliases` naar de ingress-controller. Die staat in een andere namespace, dus de chart wordt er afhankelijk van.
  - Een DNS-rewrite in CoreDNS. Die geldt voor het hele cluster, en dat verbiedt ADR 0004.
- **k6 vanaf de Mac** in een container met `--network host` werkte meteen via de voordeur, net als Playwright: 34 verzoeken per seconde, zonder fouten.
- **k6 kan zijn cijfers met OTLP naar `otel-lgtm` sturen**, via een port-forward.

Er zijn drie problemen. Ze komen uit `observability-keuzes.md`:
- **Het geld raakt op.** Een contact hoeft geen rekening bij BankSim te hebben, en er is geen endpoint voor inkomend geld.
- **Botsing met de tests.** De simulatiedatum geldt voor de hele bank (ADR 0014).
- **De database groeit.** Een betaling kost ongeveer 0,6 KB, en regels uit het grootboek kun je niet weggooien (ADR 0024).

Opties:
1. **k6 vanaf de Mac,** in een container die op digest is vastgezet (zoals ADR 0006), gestart met `make verkeer`.
2. **k6 als Deployment in de chart,** met `hostAliases` naar de ingress-controller.
3. **Een eigen generator in Java,** in een nieuwe module. Realistischer, maar veel meer code.

## Beslissing
Optie 1.
- **Tool:** `grafana/k6:2.4.0`, op digest vastgezet. Het script `deploy/verkeer/verkeer.js` logt in via het formulier van Keycloak, en gebruikt het CSRF-token en een Idempotency-Key zoals de app. `constant-arrival-rate` houdt de load gelijk, ook als de bank trager wordt.
- **Verkeersklanten:** een nieuwe opdracht `bank-seed verkeer` maakt een kring van vijf klanten, `verkeer-1` tot en met `verkeer-5`.
  - Elk heeft een betaalrekening met saldo, en elk staat als contact bij de andere.
  - De opdracht is idempotent, zoals de demo-data (ADR 0040): wat er al is, blijft staan.
  - Het wachtwoord staat in een Secret `bank-verkeer`, dat `install.sh` aanmaakt (ADR 0009).
  - Een Job `bank-verkeer` in de chart draait de opdracht na elke installatie, maar alleen met `verkeer.klanten: true`.
- **Verkeer:** de verkeersklanten lezen hun overzicht en transacties, en betalen elkaar in de kring. Zo blijft het geld in de kring, en blijft het totaal gelijk. Ongeveer 1 op de 10 betalingen is bewust te groot, voor echte weigeringen.
- **`make verkeer`:**
  1. installeert met de verkeersklanten;
  2. opent een port-forward naar `otel-lgtm`;
  3. start k6, met `SNELHEID` (klantbezoeken per seconde; een bezoek is inloggen, kijken en soms betalen) en `DUUR`.

  Ctrl-C stopt alles.
- **CI:** in de job `telemetrie` draait k6 een halve minuut op 2 per seconde, voor het scenario dat het totaal van de kring gelijk blijft. Doorlopend verkeer is er in CI niet.

## Gevolgen
- **De bank krijgt verkeer door de voordeur,** dus ook door de ingress, de BFF en Keycloak. Een vertraging is zichtbaar in de traces en het dashboard.
- **Het verkeer stopt** als de Mac slaapt of de terminal sluit. Voor een demo of een meting is dat genoeg; doorlopend verkeer dag en nacht kan zo niet.
- **Geen botsing met de tests,** zolang je `make e2e` niet tegelijk draait met `make verkeer`: de simulatiedatum geldt voor iedereen. k6 verzet de datum zelf niet.
- **De database groeit** met ongeveer 0,6 KB per betaling. Bij 5 klantbezoeken per seconde, waarvan 1 op de 20 een betaling, is dat ongeveer 0,5 MB per uur. De PVC van 1 Gi is dan na ongeveer 80 dagen verkeer vol. `make uninstall` maakt hem weer leeg, maar wist ook de demo-data.
- **De demo-huishoudens blijven ongemoeid.** k6 logt alleen in als verkeersklant.
- **Anders dan de seed-Deployment die ADR 0020 afwees:** k6 draait niet in het cluster, en de Job `bank-verkeer` draait alleen als je erom vraagt. Hij heeft geen andere rechten dan de Job `bank-demo`.

## Bewaking
- **`e2e/specs/infra/verkeer.spec.ts`** (project `infra`, in CI in de job `telemetrie`): bij een halve minuut verkeer blijft het totaal van de saldi van de verkeersklanten gelijk, en komt het verkeer aan in de HTTP-metrics.
- **De `checks` en thresholds van k6:** meer dan 1% fouten maakt een run rood.
- **helm-unittest:** de Job `bank-verkeer` is er alleen met `verkeer.klanten: true`, en haalt het wachtwoord uit het Secret.
- **bats:** `install.sh` maakt het Secret `bank-verkeer`.
- **Unittests en Testcontainers:** de opdracht `bank-seed verkeer` is idempotent.
- **Meetbaar doel:** de job `telemetrie` blijft korter dan de acceptatiejob in dezelfde run. De meting komt hieronder, in een aparte commit.
