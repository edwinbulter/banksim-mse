# 0054. bank-api en bank-bff sturen traces, metrics en logs met OpenTelemetry naar `grafana/otel-lgtm` in de namespace

Datum: 2026-11-15
Status: Geaccepteerd

## Context
De bank moet onder load te volgen zijn: wat deed een verzoek, waar zat de tijd, en wat gebeurt er met CPU en geheugen. Nu is er niets:
- Actuator toont alleen `health`;
- er is geen registry voor metrics en geen tracing;
- de hoofdcode heeft geen enkele logregel.

De afweging staat in [observability-opties.md](../observability-opties.md), het advies in [observability-keuzes.md](../observability-keuzes.md). Een spike op 2026-11-15 (niet gecommit) controleerde de aannames:
- **Trace-ID:** het trace-ID gaat vanzelf door de gateway van de BFF naar bank-api. Een trace van bank-api heeft bank-bff als begin.
- **Database:** er komen geen spans voor de database. Een trace stopt bij de controller van bank-api.
- **IBAN's:** het attribuut `http.url` bevat de IBAN, in drie spans per verzoek, in beide apps.
- **Keycloak:** Keycloak levert zijn eigen metrics (`KC_METRICS_ENABLED`, poort 9000). De Collector in `otel-lgtm` haalt ze op met een eigen configuratie.
- **`otel-lgtm`:** na een paar minuten 630 MB echt geheugen, plus ongeveer 950 MB cache. Het image is 2,6 GB.

**Opties voor de instrumentatie:**
1. `spring-boot-starter-opentelemetry` (Spring Boot 4, OTLP);
2. de OpenTelemetry Java-agent;
3. Micrometer met een Prometheus-endpoint, en aparte oplossingen voor logs en traces.

**Opties voor opslag en scherm:**
1. `grafana/otel-lgtm`: Collector, Prometheus, Tempo, Loki en Grafana in één container;
2. elk onderdeel apart in de chart;
3. een operator, zoals de kube-prometheus-stack. Die brengt CRD's en ClusterRoles mee, en dat verbiedt ADR 0004.

**CPU en geheugen per container** zijn hier niet te meten. Daarvoor zijn metrics-server of rechten op de kubelet nodig, en die gelden voor het hele cluster (ADR 0004). Elk proces meldt daarom zijn eigen CPU en geheugen.

## Beslissing
- **Instrumentatie: optie 1.** `spring-boot-starter-opentelemetry` komt in bank-api en bank-bff, met de versie van Spring Boot. Elk verzoek wordt getraced (`sampling.probability` 1.0). Voor de SQL-spans komt `net.ttddyy.observation:datasource-micrometer-spring-boot` 2.2.1 in bank-api. Die versie is gebouwd tegen Spring Boot 4.0.5. Elk gegeven krijgt `service.version` mee, de image-tag. Een verzoek met een Idempotency-Key (ADR 0027) krijgt die sleutel als attribuut `bank.idempotency_key` op zijn span. Zo is één betaalpoging terug te vinden; een UUID is geen persoonsgegeven.
- **Logs** (aangevuld in stap 3, de beslissing is dezelfde): Logback schrijft naar stdout, gestructureerd in ECS-formaat, en via `io.opentelemetry.instrumentation:opentelemetry-logback-appender-1.0` naar OTLP. Die appender zit niet in Spring Boot. Versie 2.21.0-alpha hoort bij OpenTelemetry 1.55.0 van Spring Boot 4.0.8. Een logregel krijgt het trace-ID van zijn verzoek mee.
- **Trace-ID in de melding:** elk probleembericht van bank-api krijgt het veld `traceId` (contract, ADR 0034). Een onverwachte fout wordt een 500 met probleemtype `onverwachte-fout`, zonder interne details; de fout zelf staat in de log.
- **Geen IBAN's en geen zoektermen in de telemetrie:** een `ObservationFilter` in bank-api en bank-bff vervangt elk IBAN door `{iban}` en haalt de querystring weg, in elk attribuut van een span en elk label van een metric. Logregels noemen alleen een probleemtype, nooit de melding voor de klant: die kan een naam bevatten.
- **Opslag en scherm: optie 1.** `grafana/otel-lgtm` 0.41.0 komt als Deployment in `banksim-mse`, op digest vastgezet:
  - achter `observability.enabled` in `values.yaml`, standaard aan;
  - met een `emptyDir`: de telemetrie overleeft een herstart niet, en verwijderen laat niets achter;
  - een request van 1 Gi en een limiet van 2 Gi;
  - een eigen Collector-configuratie in een ConfigMap. Die haalt de metrics van Keycloak op, en filtert namen en IBAN's (stap 3, met een eigen vangrail).
- **Keycloak:** `KC_METRICS_ENABLED`, en poort 9000 in de Service.
- **Toegang:** `make grafana` opent Grafana met een port-forward. Er komt geen derde host (ADR 0004, 0007).
- **CI:** `otel-lgtm` en de eind-tot-eind-scenario's van de telemetrie draaien alleen in de infra-job die het kortst duurt. De andere jobs installeren met `observability.enabled=false`.

## Gevolgen
- **Eén scherm.** In Grafana zie je de metrics, traces en logs van één verzoek bij elkaar: van een piek in een grafiek naar een trage trace, en van daar naar de query die de tijd kostte.
- **Geheugen.** De node heeft ongeveer 2 GiB extra nodig. Er is ongeveer 10 GiB vrij.
- **Telemetrie verdwijnt** bij elke herstart van `otel-lgtm`. Voor een demo en voor tests is dat genoeg. Geschiedenis over dagen vraagt een PVC en een nieuwe beslissing.
- **Grafana zonder inlog.** `otel-lgtm` zet anoniem inloggen aan. Dat mag, omdat Grafana alleen via een port-forward bereikbaar is.
- **`otel-lgtm` is bedoeld voor ontwikkeling, demo en test,** en dat is dit project. Een opzet zoals in productie betekent: de onderdelen apart in de chart zetten. De apps veranderen dan niet, want ze spreken alleen OTLP.
- **CPU en geheugen zoals het proces ze ziet,** niet zoals Kubernetes ze ziet. Een OOMKill, een herstart of de afstand tot de limiet van een pod zie je niet. PostgreSQL meet niets tot stap 8 van `observability-keuzes.md`.
- **`process_cpu_usage` is een fractie van alle CPU's van de node.** Het dashboard rekent het om naar cores.
- **De pipeline:** de gekozen infra-job wordt naar schatting 1 tot 2 minuten langer, door het image en het laden in kind. Die job loopt naast de langere acceptatiejob.
- **Geen tracing:** het inloggen bij Keycloak, de ingress en bank-web. Een trace begint bij de BFF.
- **`datasource-micrometer` en de Logback-appender zijn geen onderdeel van Spring Boot.** Bij een upgrade van Spring Boot hoort een controle van hun versies. De appender is een alpha-versie: zijn API kan nog veranderen. Breekt een van beide, dan valt een scenario van de telemetrie om.

## Bewaking
- **`e2e/specs/infra/observability.spec.ts`** (project `infra`, in CI in de gekozen infra-job): één betaling geeft één trace over bank-bff en bank-api, met een span voor de SQL. Een geweigerde betaling staat met hetzelfde trace-ID in de melding, in Loki en in Tempo. Later komen er scenario's bij voor de metrics (stap 4).
- **De vangrail "geen IBAN en geen naam in de telemetrie":** het scenario "De telemetrie bevat geen IBAN en geen naam van een klant" zoekt in alle traces en logregels van het scenario. `ZonderPersoonsgegevensTests` in bank-api en bank-bff test het filter.
- **helm-unittest:**
  - `otel-lgtm` staat op digest vast en heeft een `emptyDir`;
  - de chart installeert ook met `observability.enabled: false`, en dan zonder de env-variabelen voor OTLP;
  - Keycloak heeft `KC_METRICS_ENABLED`.
- **`scripts/iac-check.sh`:** alles blijft in `banksim-mse`, zonder cluster-brede resources.
- **Meetbaar doel:** de infra-job met `otel-lgtm` blijft korter dan de acceptatiejob in dezelfde run. Zo wordt de pipeline er niet langer door. Een vergelijking met een vorige run zegt weinig, want de duur van een job verschilt per runner tot 2 minuten (ADR 0052). De meting komt hieronder, in een aparte commit.

## Meting
Eerste run: CI-run 43766616522 (9fde51b, 2026-11-15).

| | Doel | Gemeten |
| --- | --- | --- |
| Duur van de job `overig-1` (met `otel-lgtm`) | korter dan de acceptatiejob | 6:29, tegen 7:23 voor de acceptatiejob |
| Cluster en installatie in `overig-1` | – | 3:05, tegen 2:25 in `overig-2` (zonder `otel-lgtm`): ongeveer 40 s voor het image en het opstarten |
| De stap Telemetrie | – | 50 s |
| Duur van de CI-run | – | 9:20; de langste job is `installatie` (7:31), zonder `otel-lgtm` |

**Het doel is gehaald.** De job met `otel-lgtm` duurt ongeveer 1:30 langer dan zonder, maar blijft bijna een minuut korter dan de acceptatiejob. De pipeline wordt er dus niet langer door.

### Tweede meting, na stap 3
Met drie telemetrie-scenario's haalde run 43847163490 (e86e864) het doel niet:
- `overig-1` duurde 9:08, de acceptatiejob 7:24, en de pipeline 10:58;
- de scenario's draaiden na elkaar (1:54);
- het opzetten met `otel-lgtm` duurde 1:47 langer dan zonder.

Daarom draaien de telemetrie-scenario's sinds 13cad55 in een eigen, vierde installatiejob `telemetrie`, met drie workers. Alleen die job heeft `otel-lgtm`. Dit vervangt "de infra-job die het kortst duurt" uit de Beslissing; het doel blijft hetzelfde.

| | Doel | Run 43849917672 (13cad55) |
| --- | --- | --- |
| Duur van de job `telemetrie` | korter dan de acceptatiejob | 5:08, tegen 6:25 voor de acceptatiejob |
| waarvan cluster en installatie met `otel-lgtm` | – | 2:48 |
| waarvan de drie scenario's, tegelijk | – | 1:18 |
| Duur van de CI-run | – | 8:39; de langste job is nu PIT voor bank-api (8:35) |

**Het doel is weer gehaald.** Er draait één runner meer per pipeline.
