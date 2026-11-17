# Observability: de keuzes

Status: de stappen 1–5 zijn gebouwd (16 november 2026), onder ADR 0054 (OpenTelemetry en `otel-lgtm`) en ADR 0055 (k6). De stappen 6–8 zijn nog voorstel.

Afwijkingen van dit voorstel, met de reden in de ADR's:
- **k6 draait vanaf de Mac** in een container, met `make verkeer`, en niet als Deployment in de chart. In een pod verwijst `bank-mse.localtest.me` naar de pod zelf (ADR 0055).
- **Geen fast-forward van de simulatiedatum.** Die werd overwogen om load te maken, maar geeft geen load: de transacties tot eind 2026 bestaan al, en een nieuwe dag maakt ze alleen zichtbaar.
- **CPU en geheugen meldt elk proces zelf**, ook Keycloak; metrics per container vragen iets voor het hele cluster (ADR 0004, 0054).
- **In CI een eigen job `telemetrie`**, met `otel-lgtm` en de verkeersklanten (ADR 0054, tweede meting).

Dit document noemt alleen de geadviseerde keuzes. De afgewogen alternatieven staan in [observability-opties.md](observability-opties.md).

## Overzicht

| Keuze | Stap |
| --- | --- |
| [OpenTelemetry in de apps](#opentelemetry-in-de-apps) | 2 |
| [`grafana/otel-lgtm` als opslag en scherm](#grafanaotel-lgtm-als-opslag-en-scherm) | 2 |
| [Grafana via port-forward](#grafana-via-port-forward) | 2 |
| [Traces en correlatie met `traceparent`](#traces-en-correlatie-met-traceparent) | 2 |
| [Regels voor telemetrie](#regels-voor-telemetrie) | Vanaf 2 |
| [Testen als CFR-slice](#testen-als-cfr-slice) | Vanaf 2 |
| [Logs naar stdout en via OTLP](#logs-naar-stdout-en-via-otlp) | 3 |
| [Metrics uit het domein](#metrics-uit-het-domein) | 4 |
| [Doorlopend verkeer met k6](#doorlopend-verkeer-met-k6) | 5 |
| [Hiërarchische health checks](#hiërarchische-health-checks) | 6 |
| [Alerts en SLO's in Grafana](#alerts-en-slos-in-grafana) | 7 |
| [PostgreSQL meten](#postgresql-meten) | 8 |

## De basis

### OpenTelemetry in de apps

**Wat het doet.** `spring-boot-starter-opentelemetry` komt in bank-bff en bank-api. De apps versturen hun metrics, traces en logs met het open protocol OTLP. Voor logs zijn ook de Logback-appender en een klein stukje bedrading nodig.

**Wat je ziet van de applicatie.** Zelf nog niets: dit levert de gegevens voor alle keuzes hieronder. Elk gegeven krijgt `service.version` mee, de image-tag van `/api/versie`, zodat je versies kunt vergelijken.

**Waarom.** De apps kennen de opslag niet, dus die is later te vervangen zonder de code aan te raken. Het is de officiële route van Spring Boot 4 en goed te testen.

### `grafana/otel-lgtm` als opslag en scherm

**Wat het doet.** Eén Deployment in namespace `banksim-mse` met de Collector, Prometheus, Tempo, Loki en Grafana erin. Het image staat op digest vast en staat achter `observability.enabled` in `values.yaml`.

**Wat je ziet van de applicatie.** Metrics, traces en logs in één scherm. Je kunt van een piek in een grafiek naar een trage trace springen, en van die trace naar de logregels van hetzelfde verzoek.

**Waarom.** Het is de snelste weg naar een werkende stack: één onderdeel in plaats van vijf. Het past binnen ADR 0004, want er zijn geen CRD's, ClusterRoles of DaemonSets voor nodig. Het image is bedoeld voor ontwikkeling, demo en test, en dat is dit project ook. Is later een opzet nodig die op productie lijkt, dan kost het uitsplitsen alleen werk aan de chart.

### Grafana via port-forward

**Wat het doet.** Een nieuw target `make grafana` opent Grafana lokaal met een port-forward.

**Wat je ziet van de applicatie.** Dit is de ingang naar alles hierboven.

**Waarom.** De afspraak is twee hosts (ADR 0004, 0007). Een derde host vraagt een ADR, en is voor een lokale demo niet nodig.

## De drie pijlers

### Traces en correlatie met `traceparent`

**Wat het doet.** Elk verzoek krijgt een trace-ID, dat met de W3C-header `traceparent` van de BFF naar bank-api gaat. `NachtelijkeAfsluiting` krijgt een eigen trace. `ProbleemAdvice` zet het trace-ID in de foutmelding; die wijziging begint in `api/bank-api.yaml` (ADR 0034).

**Wat je ziet van de applicatie.** De route en de duur van een verzoek per stap, en dus of een trage betaling in de BFF of in bank-api zit. Vanuit een foutmelding kom je direct bij het verzoek dat misging. Een trace begint bij de BFF, omdat de ingress buiten de namespace staat.

**Waarom.** Het is de standaard, en de ID's leggen ook de relaties tussen de stappen vast. Een eigen `X-Correlation-ID` zou je zelf moeten doorgeven en doet minder.

### Logs naar stdout en via OTLP

**Wat het doet.** Bank-api en bank-bff schrijven gestructureerde logregels met het trace-ID, zowel naar stdout als via OTLP naar Loki. Er wordt alleen gelogd wat betekenis heeft: een geweigerde betaling, een afgeronde maandafsluiting, elke onverwachte fout.

**Wat je ziet van de applicatie.** Waarom iets misging, doorzoekbaar over beide apps en ook nadat een pod weg is. Alle regels van één verzoek staan bij elkaar.

**Waarom.** De hoofdcode van de backend had geen enkele logregel, dus dit was de grootste winst bij een storing. OTLP vraagt geen DaemonSet en blijft dus binnen ADR 0004. Stdout vangt ook wat de app bij een crash niet meer kan versturen.

### Metrics uit het domein

**Wat het doet.** Tellers voor betalingen per uitkomst, weigeringen door "nooit rood" (ADR 0025) en herhalingen met dezelfde Idempotency-Key (ADR 0027), en de duur van de maandafsluiting (ADR 0028). De JVM- en HTTP-metrics komen vanzelf mee.

**Wat je ziet van de applicatie.** Of de bank werkt, niet alleen of de machine draait. Een piek in weigeringen of een afsluiting die opeens lang duurt, valt op, ook als de techniek er gezond uitziet.

**Waarom.** Deze metrics zeggen het meest, kosten weinig en vragen geen nieuwe tool.

## Doorlopend verkeer met k6

Gebouwd onder ADR 0055. Hoe je ermee test, staat in [Kwaliteitscontroles, hoofdstuk 6](kwaliteitscontroles.md#6-testen-onder-load).

**Wat het doet.** `make verkeer` start k6 op de Mac, in een container die op digest is vastgezet, met `--network host`. Het script `deploy/verkeer/verkeer.js` doet zich voor als de verkeersklanten `verkeer-1` tot en met `verkeer-5`. Het logt in via het formulier van Keycloak (`directAccessGrantsEnabled` staat uit), en het verkeer loopt door de voordeur: ingress → BFF → bank-api. Met `constant-arrival-rate` blijft de belasting gelijk als het systeem trager wordt. Een run heeft een vaste duur (`DUUR`), en Ctrl-C stopt hem eerder. De cijfers van k6 gaan via een port-forward naar `otel-lgtm`.

Het voorstel was een Deployment in de chart. Dat werkte niet: in een pod verwijst `bank-mse.localtest.me` naar 127.0.0.1, en dat is de pod zelf (ADR 0055).

**Wat je ziet van de applicatie.**

- Gevulde grafieken en traces terwijl k6 draait, zodat je versies onder dezelfde load kunt vergelijken.
- Vertraging onder gelijkblijvende belasting. Bij een gesloten model zou de belasting juist afnemen zodra het systeem traag wordt, en dan valt de vertraging weg.
- De duur zoals de klant hem ziet, inclusief de ingress en Keycloak, naast de duur die de BFF en bank-api zelf meten.
- Echte weigeringen, doordat een klein deel van de betalingen bewust te groot is.

**Waarom.** Zonder verkeer heeft de rest weinig te laten zien. k6 is weinig code en raakt de backend niet. Een eigen Java-module zou realistischer zijn, maar kost veel meer code en onderhoud. Een generator die direct in de database schrijft, geeft geen traces en omzeilt `LedgerService` (ADR 0024).

**Hoe de bekende problemen zijn opgelost.**

| Probleem | Oplossing |
| --- | --- |
| Het geld raakt op, omdat een contact geen rekening bij BankSim hoeft te hebben en er geen endpoint is voor inkomend geld | Een kring van verkeersklanten die elkaar betalen, aangemaakt met `bank-seed verkeer`. De Job `bank-verkeer` draait die opdracht na een installatie met `verkeer.klanten: true`, en `make verkeer` zet dat aan. Het totaal blijft gelijk, en `verkeer.spec.ts` controleert dat |
| Botsing met de tests, omdat de simulatiedatum voor de hele bank geldt (ADR 0014) | k6 draait alleen als je `make verkeer` start, en nooit tegelijk met `make e2e`. In CI draait het een halve minuut in de job `telemetrie`, naast alleen de scenario's van `observability.spec.ts`, en die verzetten de datum niet. De verkeersklanten hebben eigen gebruikersnamen en raken de demo-klanten niet |
| De database groeit, want de PVC is 1 Gi en regels uit het grootboek kun je niet weggooien (ADR 0024) | Veel lezen en weinig schrijven: 1 op de 20 bezoeken betaalt. Gemeten: ongeveer 0,6 KB per betaling. Bij 5 bezoeken per seconde is dat ongeveer 0,5 MB per uur, en is de PVC na ongeveer 80 dagen verkeer vol |
| Een maandafsluiting is zeldzaam | De beheerder zet de datum in een demo met de hand vooruit |

## Gezondheid en signalering

### Hiërarchische health checks

**Wat het doet.** Een eigen Actuator-groep `systeem`: de BFF vraagt bank-api, en die controleert de database en Keycloak. Een poller zet de uitkomst om in een metric. Er komt een korte time-out of cache, omdat elke poll extra aanroepen doet.

**Wat je ziet van de applicatie.** Op één adres of de hele keten werkt en welke schakel niet, en in Grafana hoe dat zich over de tijd ontwikkelde.

**Waarom.** Dit staat bewust los van de liveness- en readiness-probes. Anders haalt een storing bij Keycloak gezonde pods uit de lucht, en dat botst met de geplande CFR Resilience.

### Alerts en SLO's in Grafana

**Wat het doet.** Een afgesproken doel, bijvoorbeeld welk deel van de betalingen binnen hoeveel tijd moet slagen, met een alert in de ingebouwde alerting van Grafana.

**Wat je ziet van de applicatie.** Of de bank haar doel haalt, zonder dat iemand naar een dashboard hoeft te kijken.

**Waarom.** "Goed genoeg" wordt zo een getal, en een alert gaat over wat de klant merkt in plaats van over CPU. Een losse Alertmanager is niet nodig. Bij een lokale demo zit de waarde vooral in de afspraak en de zichtbare status.

### PostgreSQL meten

**Wat het doet.** `postgres_exporter` met `pg_stat_statements`. Dit vraagt een nieuw image met een ADR, en een aanpassing van `shared_preload_libraries`.

**Wat je ziet van de applicatie.** De zwaarste queries, de wachttijden op locks, het aantal deadlocks en de grootte van de database.

**Waarom.** Dit beantwoordt open vragen: of de `pg_trgm`-index nodig is, of de datumquery per verzoek een cache vraagt (ADR 0013), en hoe de locking van ADR 0026 en 0039 zich gedraagt. Het waarschuwt ook op tijd voor een volle PVC. Dit staat achteraan omdat traces een deel van deze vragen al kunnen beantwoorden, als de SQL-spans werken.

## Regels voor telemetrie

Deze regels laten zelf niets zien, maar bepalen wat er wel en niet in de telemetrie mag.

| Regel | Wat het doet | Waarom |
| --- | --- | --- |
| Geen namen of IBAN's | Filtert ook URL's en zoektermen, en wordt bewaakt door een vangrail | Paden als `/api/rekeningen/{iban}/transacties` bevatten een IBAN, en zoektermen bevatten namen |
| UTC, met `bank.datum` apart | Scheidt de echte tijd van de bankdag | De tooling verwacht echte tijd, en de simulatiedatum springt (ADR 0013) |
| Aantallen of bedragklassen, geen bedragen | Houdt `double` uit de eigen code | ADR 0010 en `GeenDrijvendeKommaTests`: een Gauge met een eigen `double`-functie laat die test falen |
| Waarden met veel varianten op spans | Houdt ze van de metric-labels af | Anders loopt Prometheus vol |

## Testen als CFR-slice

**Wat het doet.** Dit wordt een CFR-slice in `doc/werkwijze-mse.md`, hoofdstuk 7, met beweringen die gedrag aantonen. Het opvragen bij Tempo en Loki komt in een driver en de bewering in de DSL (ADR 0031).

| Bewering | Waar |
| --- | --- |
| Eén betaling geeft één trace over BFF en bank-api, met de SQL | `e2e/specs/infra/observability.spec.ts` |
| De logregels van die betaling staan centraal, met hetzelfde trace-ID | Hetzelfde scenario |
| Een geweigerde betaling staat met hetzelfde trace-ID in de melding, in Loki en in Tempo; een onverwachte fout geeft een 500 met een trace-ID | Hetzelfde bestand; `OnverwachteFoutTests` |
| Valt de database weg, dan meldt `systeem` een fout en blijft de liveness goed | Infrascenario, `@destructief`, alleen in CI |
| Geen namen of IBAN's in telemetrie | Vangrail in `observability.spec.ts` en `ZonderPersoonsgegevensTests`, rood gezien met sabotage |
| Telemetrie staat in UTC, met `bank.datum` apart | Unittest |
| Het totaal van de verkeersklanten blijft gelijk | `e2e/specs/infra/verkeer.spec.ts`, via de DSL |
| De chart installeert ook met `observability.enabled: false`, en alles staat in `banksim-mse` | helm-unittest en `scripts/iac-check.sh` |

**Waarom.** Alleen controleren of er een `traceparent`-header is, bewijst niet dat er een bruikbare trace ontstaat.

## Volgorde

1. **Spike, zonder commit:** `otel-lgtm` en k6 lokaal draaien en de punten onderaan dit document uitzoeken.
2. **ADR (eerstvolgende vrije nummer) en traces:** de starter, de chart, `make grafana` en het trace-scenario groen.
3. **Logs:** gestructureerde regels met trace-ID, en het trace-ID in foutmeldingen.
4. **Metrics uit het domein** en een eerste dashboard, als bestand in de chart.
5. **k6:** de kring van verkeersklanten en `make verkeer`.
6. **Health checks:** de groep `systeem` met de poller.
7. **Alerts en SLO's.**
8. **PostgreSQL.**

## Nog te maken keuzes

| Keuze | Advies |
| --- | --- |
| Staat de telemetrie aan in CI? | Besloten: alleen in de eigen job `telemetrie`, met een halve minuut k6 (ADR 0054 en 0055, met de metingen) |
| Wanneer beginnen? | Direct na S11.1. De demo-data is er dan al, zodat de metingen meteen iets zeggen |
| Welke SLO-waarden? | Geen advies zonder meting: eerst een week aan cijfers uit het doorlopende verkeer |
| Welke vraag beantwoordt de demo? | Eén of twee, met een voor- en nameting. Mijn voorkeur: de cache van ADR 0013 of de `pg_trgm`-index |
| Komt de Idempotency-Key als attribuut op de span? | Besloten: ja, als `bank.idempotency_key` (ADR 0054) |

### Aanvullingen, nog zonder moment

| Aanvulling | Wat je ziet van de applicatie |
| --- | --- |
| Controle op de boekhouding | Of alle overboekingen samen nul zijn en geen rekening onder nul staat, ook na een migratie of handmatige wijziging |
| Keycloak en beveiliging | Mislukte logins, en de aantallen 401, 403 en 404. Inloggen valt nu buiten elke trace |
| Frontend (Grafana Faro of OpenTelemetry-libraries) | Fouten en snelheid in de browser, en of een trage betaling in de browser of de backend zit. Vraagt een publiek adres en raakt de CSP |
| Status van de pods | Restarts, OOMKills en Kubernetes-events |
| Logs van Keycloak en PostgreSQL via een agent | Fouten buiten onze eigen apps, centraal naast de andere logs |
| DORA-metingen van de pipeline | Niet de applicatie maar de werkwijze: hoe vaak we opleveren, hoeveel runs falen en hoe snel dat is hersteld |
| Profiling met Pyroscope | Welke methode de CPU en het geheugen kost, als een trace zegt welke stap traag is |

## Uitkomst van de spike

De spike was op 15 november 2026; de uitkomsten staan in ADR 0054 en 0055.

| Aanname | Uitkomst |
| --- | --- |
| Het geheugengebruik van `otel-lgtm` en k6 naast de bank | `otel-lgtm` gebruikte na een paar minuten 630 MB echt geheugen, plus ongeveer 950 MB cache. k6 draait op de Mac en kost de node niets |
| De `traceparent` loopt vanzelf door de gateway van de BFF | Klopt |
| Er komen SQL-spans | Niet vanzelf; daarvoor is `datasource-micrometer-spring-boot` gekomen (ADR 0054) |
| IBAN's komen in de spans terecht | Ja, in `http.url`, in drie spans per verzoek. Een `ObservationFilter` in de apps haalt ze eruit, niet de Collector |
| De Collector kan de health-uitkomst pollen zonder eigen configuratie | Niet uitgezocht; hoort bij stap 6. De Collector heeft al een eigen configuratie, voor Keycloak |
| Hoe snel de database groeit | Ongeveer 0,6 KB per betaling |
| Een agent en `kube-state-metrics` hebben genoeg aan een namespaced Role | Niet uitgezocht; alleen van belang voor de aanvullingen |
