# Observability: mogelijkheden en afwegingen

Dit document beschrijft hoe BankSim-MSE observability kan demonstreren volgens de uitleg van Dave Farley. Het geeft per keuze de mogelijkheden met hun voor- en nadelen, en een aanbeveling. De beslissingen zelf staan aan het eind open; daarna volgen de ADR's.

Status: voorstel, 2 november 2026.

## 1. De maatstaf: wat Farley vraagt

Farley noemt observability het vermogen om data te verzamelen over de uitvoering, de interne status en de communicatie tussen componenten, om te begrijpen wat software in productie doet. Meten is voor hem het verschil tussen engineering en ambacht, en sommige lessen over prestaties en gebruikerswaarde leer je alleen in productie.

| Onderdeel bij Farley | Wat het vraagt | Stand in BankSim-MSE |
| --- | --- | --- |
| Metrics | Metingen op een moment | Geen |
| Logs | Chronologische gebeurtenissen en fouten | Geen enkele logregel in de hoofdcode van de backend |
| Traces | De route van een verzoek door het systeem | Geen |
| Tijdsynchronisatie | Timestamps in UTC, klokken via NTP | Pods draaien in UTC; de bankdag is Europe/Amsterdam (ADR 0013) |
| Hiërarchische health checks | Elke service is te pollen op pass/fail en geeft de check door aan zijn afhankelijkheden | Alleen liveness en readiness, op poort 8081 |
| Log-aggregatie | Niet lokaal, maar centraal bijeen | Geen |
| Correlatie-ID's | Unieke ID's en hun relaties in elk bericht | Geen |

**Wat BankSim-MSE niet goed kan laten zien.** Dit hoort eerlijk in de demo:

- **Klokverschillen:** alles draait op één node, dus de klokken lopen gelijk.
- **Wisselende routes:** de services roepen elkaar synchroon aan en de route ligt vast. `OverboekingGeboekt` loopt in dezelfde transactie (ADR 0030) en is dus niet asynchroon.
- **Gebruikerswaarde:** gegenereerd verkeer gedraagt zich zoals wij het bedenken. Het toont prestaties en fouten, maar zegt niets over wat echte klanten waarderen.

## 2. Doorlopend verkeer

Het verkeer moet door de voordeur komen (ingress → BFF → bank-api), anders ontstaan er geen volledige traces. De realm staat `directAccessGrantsEnabled: false`, dus elke generator moet inloggen via het formulier van Keycloak, zoals `e2e/drivers/rest/rest-driver.ts` nu doet.

| Mogelijkheid | Voordelen | Nadelen |
| --- | --- | --- |
| **A. k6 als gewone Deployment** (zonder k6-operator, die CRD's meebrengt) | Weinig code en licht. Open belastingsmodel (`constant-arrival-rate`): de belasting blijft gelijk als het systeem traag wordt, zodat vertraging niet wegvalt. Raakt de backend niet | Nieuwe tool, dus een ADR. Inlogflow opnieuw schrijven. Een script is geen getest domeinmodel. Een run heeft een eindige duur, dus de pod start periodiek opnieuw |
| **B. Java-module `bank-verkeer`** | Geen nieuwe tool. Realistische huishoudens met salaris, huur en dagritme. Valt onder JUnit, PIT en de vangrails. Kan delen met de generator van S11.1 | Veel meer code en onderhoud. Een open belastingsmodel moet je zelf bouwen. Op zijn sterkst pas na slice 11 |
| **C. REST-driver of DSL als doorlopend proces** | Inloggen, CSRF en Idempotency-Key bestaan al. Zelfde taal als de scenario's | De driver is gebouwd rond Playwright en `expect`, niet om dagen te draaien. Zwaar image. Testcode wordt een productieonderdeel |
| **D. Direct in de database, of een scheduler in bank-api** | Heel eenvoudig | Geen HTTP- of BFF-traces. Omzeilt `LedgerService` (ADR 0024), of zet demo-code in de bank |

**Aanbeveling: A.** Het levert het snelst verkeer, en bij S11.1 kan k6 inloggen met de 10 gegenereerde huishoudens. Kies B alleen als de demo meer realisme vraagt. D valt af.

ADR 0020 wees een permanente seed-Deployment af, omdat testgereedschap niet in de installatie hoort. De nieuwe ADR moet uitleggen waarom dit anders is: de generator is een demo-onderdeel achter een schakelaar in `values.yaml`, zonder extra rechten.

### Wat elke generator moet oplossen

| Probleem | Waarom | Voorstel |
| --- | --- | --- |
| Het geld raakt op | Een contact hoeft geen rekening bij BankSim te hebben (`V5__contacten.sql`), en er is geen endpoint voor inkomend geld | Een kring van verkeersklanten die elkaar betalen, aangemaakt met `bank-seed`. Het totaal blijft gelijk, en dat is te controleren. Een klein deel is bewust te groot, voor echte weigeringen door "nooit rood" |
| Botsing met de tests | De simulatiedatum geldt voor de hele bank (ADR 0014) | De generator pauzeert tijdens testruns via een Helm-waarde en staat in CI uit. Eigen aliassen voor verkeersklanten |
| De database groeit | De PVC is 1 Gi, en saldi volgen uit het grootboek, dus regels kun je niet weggooien (ADR 0024). Grove schatting: 1 boeking per seconde vult hem in een week of twee | Veel lezen (2–5 per seconde), weinig schrijven (ongeveer 3 per minuut), tijdelijk opschalen in een demo. Eerst meten, en de PVC zo nodig vergroten |
| Een maandafsluiting is zeldzaam | In echte tijd gebeurt ze één keer per maand | De beheerder zet in de demo de datum met de hand vooruit. Automatisch versnellen valt af: de datum geldt voor iedereen, en de datumkiezer stopt op 31 december 2026 |

## 3. Tooling voor de drie pijlers

### Instrumentatie

| Mogelijkheid | Voordelen | Nadelen |
| --- | --- | --- |
| **A. `spring-boot-starter-opentelemetry`** (Spring Boot 4, OTLP) | Officiële route, voor metrics, traces en logs. Apps spreken alleen OTLP, dus de backend is later te vervangen. Goed te testen | Handmatig domeinattributen toevoegen (dat is ook het doel). Minder automatische dekking dan de agent |
| **B. OpenTelemetry Java-agent** | Veel automatisch, zonder codewijziging | Lastiger te testen. Extra onderdeel in het Jib-image. Levert vooral techniek, geen domeintaal |
| **C. Micrometer met Prometheus-scrape, plus aparte oplossingen voor logs en traces** | Bekend en eenvoudig voor metrics | Drie aparte wegen naar buiten, met meer koppeling |

**Aanbeveling: A.**

### Backend

| Mogelijkheid | Voordelen | Nadelen | Geschikt voor dit cluster? |
| --- | --- | --- | --- |
| **A. `grafana/otel-lgtm`** (één image: Collector, Prometheus, Tempo, Loki, Grafana) | Snelst werkend. Eén Deployment en één digest. Alle drie de pijlers in één scherm | Bedoeld voor ontwikkeling, demo en test. Minder fijn af te stellen. Beperkte retentie | Ja |
| **B. Prometheus, Loki, Tempo, Grafana en Collector als eigen templates** | Lijkt op een echte productie-opzet. Alles in de chart, getest met helm-unittest. Alertregels testbaar met `promtool` | Vijf onderdelen met configuratie en opslag. Meeste werk en geheugen | Ja, zonder de upstream-charts met ClusterRoles en DaemonSets |
| **C. OpenObserve** | Eén binary voor alle drie de pijlers. Licht volgens de leverancier | Kleiner ecosysteem. Minder overdraagbaar als voorbeeld | Waarschijnlijk; nog te controleren |
| **D. SigNoz** | Eén interface, gebouwd op OpenTelemetry | De documentatie noemt minimaal 8 GB en 4 cores op Kubernetes | Nee, te zwaar naast de bank (node ~11,7 GiB) |

**Aanbeveling: A, met B als vervolgstap.** Dit is een demo op één node. Doordat de apps alleen OTLP spreken, kost de overstap later alleen chartwerk.

**Wat ADR 0004 uitsluit:** geen cluster-brede resources, dus geen `kube-prometheus-stack`, ServiceMonitor-CRD's, node-exporter of kube-state-metrics. De ingress staat in een andere namespace, dus een trace begint bij de BFF.

**Grafana bereiken:** de afspraak is twee hosts. Begin met `make grafana` via port-forward; een derde host vraagt een ADR.

## 4. Farleys vier patronen

### 4.1 Tijdsynchronisatie

| Mogelijkheid | Voordelen | Nadelen |
| --- | --- | --- |
| **A. Telemetrie in UTC, met de bankdatum als apart attribuut `bank.datum`** | Volgt Farley. Scheidt de echte tijd van de simulatiedatum, zodat je ook kunt zien op welke bankdag iets gebeurde | Twee tijdsbegrippen om uit te leggen |
| **B. Alles in Europe/Amsterdam** | Leest prettig | Gaat tegen Farley in; dubbele en ontbrekende uren bij de zomertijd |
| **C. Telemetrie volgt de simulatiedatum** | Tijdlijn past bij de bank | Tijdlijnen springen. De tooling verwacht echte tijd, dus dit werkt niet |

**Aanbeveling: A.** NTP regelt de host; op één node is klokverschil niet te demonstreren. Dat staat in de demo als beperking.

### 4.2 Hiërarchische health checks

Let op een valkuil: als een afhankelijkheid in de liveness- of readiness-probe komt, kan een storing bij Keycloak de hele bank uit de lucht halen. Dat botst ook met de geplande CFR Resilience: "API en BFF starten zonder database of Keycloak". De hiërarchie hoort dus los van de probes.

| Mogelijkheid | Voordelen | Nadelen |
| --- | --- | --- |
| **A. Een eigen Actuator-healthgroep `systeem`, los van de probes** | bank-bff vraagt bank-api, die de database en Keycloak controleert. Dat is de doorgegeven check van Farley, met bestaande middelen | Eigen `HealthIndicator`s. Bij elke poll extra aanroepen, dus een korte time-out of cache nodig |
| **B. Alleen van buitenaf pollen** (Collector of k6) | Geen wijziging in de apps | Elke check staat los. Geen hiërarchie, dus niet wat Farley bedoelt |
| **C. Afhankelijkheden in de bestaande probes** | Minste code | Kubernetes haalt dan gezonde pods uit de lucht bij een storing elders. Afgeraden |

**Aanbeveling: A.** Een poller zet de uitkomst om in een metric, zodat de gezondheid over de tijd in Grafana staat. Of de Collector in `otel-lgtm` dat zonder eigen configuratie kan, is nog te controleren.

### 4.3 Log-aggregatie

Er zijn nu geen logregels, dus eerst moet worden bepaald wat er gelogd wordt: gestructureerd, en met correlatie-ID. Bijvoorbeeld een geweigerde betaling, een afgeronde afsluiting en elke onverwachte fout.

| Mogelijkheid | Voordelen | Nadelen |
| --- | --- | --- |
| **A. OTLP-push vanuit de apps** | Geen node-toegang nodig, past in de namespace. Trace-ID komt vanzelf mee. Weinig configuratie | Alleen bank-api en bank-bff. Logs die de app niet meer kan versturen bij een crash gaan verloren |
| **B. Via de Kubernetes-API met een agent en een namespaced Role** (bijv. Grafana Alloy) | Ook Keycloak en PostgreSQL. Ook crashuitvoer via stdout | Extra onderdeel en een Role. Nog te controleren of dit binnen `banksim-mse` kan zonder cluster-brede rechten |
| **C. DaemonSet met hostPath** | Standaardaanpak, alles op de node | Verboden terrein (ADR 0004): ziet ook andere namespaces |
| **D. Pub/sub** (Kafka, NATS), Farleys voorbeeld | Sluit aan bij zijn uitleg, ontkoppelt de logstroom | Zware extra infrastructuur, alleen voor logs |
| **E. Alleen `kubectl logs`** | Niets te bouwen | Geen aggregatie; verdwijnt met de pod. Voldoet niet |

**Aanbeveling: A, plus gestructureerde uitvoer op stdout.** B volgt als de demo ook logs van Keycloak of PostgreSQL nodig heeft.

### 4.4 Correlatie-ID's

Farley vraagt unieke ID's én hun relaties in de context van elk bericht. Trace-ID, span-ID en parent-ID doen precies dat.

| Mogelijkheid | Voordelen | Nadelen |
| --- | --- | --- |
| **A. W3C `traceparent` via OpenTelemetry** | Standaard, en werkt vanzelf door de BFF en naar bank-api. Bevat de relaties. Koppelt logs aan traces | Minder zichtbaar in de domeintaal. Geen eigen betekenis |
| **B. Eigen `X-Correlation-ID`** | Eenvoudig uit te leggen, sluit direct aan bij Farley | Zelf doorgeven, en geen relaties. Dubbel met A |
| **C. Idempotency-Key als correlatie-ID** | Bestaat al, en is betekenisvol: één betaalpoging | Alleen bij schrijfacties. Hoort niet in telemetrie zonder afspraak |

**Aanbeveling: A, met de Idempotency-Key eventueel als attribuut op de span.**

Twee aandachtspunten:

- **Taken zonder verzoek:** `NachtelijkeAfsluiting` krijgt een eigen trace, anders is ze nergens aan te koppelen.
- **Foutmeldingen:** `ProbleemAdvice` kan het trace-ID meegeven, zodat een melding terug te vinden is. Dat is een klein extra scenario.

## 5. Regels voor telemetrie

- **Geen namen of IBAN's in telemetrie**, ook al zijn ze fictief. Dit wordt een vangrail, met sabotage om rood te zien.
- **Velden met veel unieke waarden** gaan op spans en niet op metric-labels, anders loopt Prometheus vol.
- **Tel aantallen of gebruik bedragklassen.** Micrometer-tellers werken met `double`, en bedragen mogen dat niet (ADR 0010).

## 6. Wat de demo kan leren

Farley benadrukt empirisch leren. De sterkste demo gebruikt daarom geen nepstoringen, maar open vragen die dit project al heeft:

| Vraag | Bron | Wat te meten |
| --- | --- | --- |
| Wordt zoeken trager als de data groeit? | Backlog: `pg_trgm`-index | Duur van de SQL-span, voor en na de index |
| Is de extra query per verzoek voor de datum erg? | ADR 0013: "een cache volgt pas als metingen erom vragen" | Aandeel van die query per trace |
| Komt de deadlock tussen afsluiting en opname echt voor? | Backlog | Mislukte traces tijdens een afsluiting |
| Wat doet een nieuwe versie met de betalingen? | Doorlopend verkeer | Vergelijken op `service.version` (de image-tag) |
| Wat merkt de bank als een afhankelijkheid wegvalt? | Patroon 4.2 | Health-status, logs en traces naast elkaar |

## 7. Past in de werkwijze

Dit wordt een CFR-slice in `doc/werkwijze-mse.md` hoofdstuk 7, met uitvoerbare beweringen:

| Bewering | Hoe |
| --- | --- |
| Een betaling met een eigen `traceparent` geeft één trace over BFF, bank-api en SQL | Infrascenario `e2e/specs/infra/observability.spec.ts` |
| Logregels van die betaling bevatten hetzelfde trace-ID en staan centraal | Hetzelfde scenario |
| Valt de database weg, dan meldt `systeem` op de BFF een fout, terwijl de liveness goed blijft | Infrascenario, alleen `@destructief` in CI |
| Geen namen of IBAN's in telemetrie | Nieuwe vangrail |
| Telemetrie staat in UTC, met `bank.datum` apart | Unittest |
| Het totaal van de verkeersklanten blijft gelijk | Bewering via de DSL of de database |

## 8. Voorgestelde volgorde

1. **Spike, zonder commit:** `otel-lgtm` en k6 lokaal draaien, en geheugen, CPU en databasegroei meten. De getallen in dit document zijn grotendeels schattingen.
2. **Correlatie en traces:** de starter in bank-bff en bank-api, `otel-lgtm` in de chart, en het trace-scenario groen.
3. **Logs en metrics:** gestructureerde logregels met het ID, en tellers voor boekingen en weigeringen.
4. **Health checks:** de groep `systeem`, met polling en een grafiek.
5. **Generator:** kring van klanten, pauze tijdens tests.
6. **Demoverhaal:** één of twee vragen uit hoofdstuk 6 echt beantwoorden, met een voor- en nameting.

## 9. Open beslissingen

1. **Generator:** k6 (aanbevolen) of Java-module?
2. **Backend:** `otel-lgtm` (aanbevolen) of direct losse onderdelen? Dat laatste is alleen beter als de productie-opzet zelf deel van het voorbeeld moet zijn.
3. **CI:** alleen aan in de job met installatiescenario's (aanbevolen, generator uit), overal aan, of alleen lokaal? Bij alleen lokaal bewaakt de pipeline de beweringen niet.
4. **Moment:** na S10 en vóór S11 (aanbevolen: dan zie je de groei van de data), of na S11, met de 10 huishoudens vanaf het begin?

## 10. Bronnen

- [OpenTelemetry with Spring Boot (Spring-blog)](https://spring.io/blog/2025/11/18/opentelemetry-with-spring-boot/)
- [grafana/docker-otel-lgtm](https://pkg.go.dev/github.com/grafana/docker-otel-lgtm)
- [SigNoz op Kubernetes](https://signoz.io/docs/install/kubernetes/others)
- [Observability Stacks Comparison](https://knowledge-base-4bl.pages.dev/observability/comparisons/observability-stacks-comparison)
- [OpenObserve over het vervangen van LGTM](https://openobserve.ai/blog/replacing-grafana-lgtm-stack.md) (leveranciersbron)
- [Vergelijking van k6, Locust en Gatling](https://www.pistack.xyz/posts/k6-vs-locust-vs-gatling-self-hosted-load-testing-guide-2026/)

Niet uit deze bronnen, maar uit mijn eigen kennis en dus nog te controleren: de mogelijkheden van Grafana Alloy en de nadelen van de Java-agent. De geheugen- en groeicijfers zonder bron zijn schattingen.
