# BankSim-MSE

BankSim-MSE bouwt [BankSim](https://github.com/edwinbulter/banksim) opnieuw: een simulatie van internetbankieren voor 10 fictieve huishoudens met ongeveer 5 jaar transactiehistorie, waarbij een beheerder de simulatiedatum kan verzetten.

Het origineel is in één keer door AI gegenereerd uit een vooraf geschreven functioneel en technisch ontwerp. Dit project levert **dezelfde applicatie** op, met **dezelfde techstack**, maar volgens de werkwijze uit *Modern Software Engineering* van Dave Farley: leren in kleine stappen met snelle feedback, en complexiteit beheersen met modulair, testbaar ontwerp.

> **Status:** stap 0 (walking skeleton) is af. Van slice 1 is story S1.1 gebouwd: een klant logt in via Keycloak en de BFF, en ziet de betaalrekening met saldo op <https://bank-mse.localtest.me>. Het scenario slaagt via de API en in drie browsers, lokaal en in de pipeline. Een upgrade behoudt de data (installatiescenario, alleen in CI). Daarmee is slice 1 af. Ook slice 2 is af: de klant opent de betaalrekening en ziet de transacties per datum, van nieuw naar oud, met teken (S2.1). Een rekening zonder transacties toont de melding dat er nog geen af- of bijschrijvingen zijn. Een lange lijst komt in porties van 50, met "Toon meer" (S2.2). Slice 2a (navigatie) is toegevoegd en af: elk scherm heeft een eigen URL (Angular-router, ADR 0012). Vanaf de betaalrekening gaat de klant terug naar het overzicht met de link "Overzicht" of met de terugknop van de browser, en herladen houdt de betaalrekening open (S2a.1). Slice 3 is af, op het scherm na (dat komt in S10.2): de beheerder zet de bank op een gekozen datum, en een klant ziet dan geen transacties van na die datum (ADR 0013). Vooruitzetten maakt ze weer zichtbaar (S3.1). Slice 4 is af: een transactie klapt uit met het type, Van en Naar met naam en IBAN (geen IBAN bij een betaalautomaat), het tijdstip en de uitvoerdatum, in Europe/Amsterdam (S4.1). Slice 5 is af: zoeken in de transacties op naam of IBAN (ook met spaties), bedrag (absoluut, met komma), type en richting, met een melding bij een ongeldig bedragbereik (S5.1). Alleen de rol `beheerder` mag dat (ADR 0015), met CSRF-bescherming in de BFF (ADR 0016). Realm-wijzigingen bereiken ook een bestaande installatie (ADR 0017). Deze scenario's draaien na de andere, één voor één (ADR 0014).

## Doel

- **Zelfde eindresultaat, andere weg.** Dezelfde schermen en regels als het origineel: overzicht, betaalrekening met zoeken, betalen naar contacten, spaarrekening met rente, overschrijven en een beheerscherm met simulatiedatum. Het functioneel en technisch ontwerp van het origineel worden geen blauwdruk, maar een bron van voorbeelden en kandidaat-beslissingen.
- **Gestuurd door voorbeelden (BDD).** Elke functie begint als een uitvoerbaar scenario in domeintaal, als Playwright-spec met een interne DSL. Hetzelfde scenario draait via de API en via de browser.
- **Double-loop outside-in TDD.** De buitenste lus is een falend acceptatiescenario, de binnenste lus is rood → groen → refactor per klasse, van de buitenkant naar binnen.
- **Beslissingen als ADR's.** Architectuurkeuzes (grootboek, `BigDecimal`, BFF, keyset-paginering, mTLS, …) worden pas vastgelegd als een slice erom vraagt, elk met een geautomatiseerde bewaking.
- **Vanaf dag 1 deploybaar.** Eerst een walking skeleton met een deployment pipeline, daarna verticale slices die elk gedeployed en bruikbaar zijn. Ook de infrastructuur (Helm-chart en scripts) wordt test-first gebouwd.
- **AI als pair programmer binnen de lus**, met per stap één falend scenario, in plaats van het hele systeem in één keer genereren.

## Omgeving en techstack

- Het bestaande kind-cluster `single-node` (OrbStack). Alles komt in namespace **`banksim-mse`**, naast het origineel in `banksim`.
- De app is bereikbaar op `https://bank-mse.localtest.me`, Keycloak op `https://auth-mse.localtest.me`.
- De techstack is dezelfde als in het origineel: Java 21, Spring Boot, Spring Cloud Gateway (BFF), Keycloak, PostgreSQL met Flyway, Angular en TypeScript, Playwright, Maven, Jib, Helm, kind en GitHub Actions.
- De enige uitbreiding zijn testtools voor de infrastructuur: helm-unittest, kubeconform, conftest, bats, shellcheck, hadolint en actionlint.

## Aanpak in het kort

1. **Stap 0**: walking skeleton en deployment pipeline (commit stage, acceptance stage in een tijdelijk kind-cluster, deploy naar `banksim-mse`).
2. **Elf functionele slices**: overzicht, transactielijst, simulatiedatum, uitklapdetails, zoeken, inloggen, betalen, spaarrekening en rente, overschrijven, beheer en realistische data.
3. **Slices voor eisen die over de hele applicatie gaan**: zero trust (mTLS, NetworkPolicies), resilience, rate limiting, hardening en supply chain, elk met een uitvoerbare bewering.

## Aan de slag

Vereisten: Java 21, Node 24 (lokaal via `/opt/homebrew/opt/node@24/bin`; de `Makefile` zet hem in het `PATH`), Docker of OrbStack, kind, kubectl en Helm 4. Het cluster `kind-single-node` moet ingress-nginx hebben op poort 80/443.

```bash
make commit-stage   # backend, frontend en infrastructuurchecks (zoals de commit stage in CI)
make install        # images bouwen en installeren in namespace banksim-mse
make e2e-install    # eenmalig: Playwright en browsers
make e2e            # acceptatietests tegen https://bank-mse.localtest.me
make e2e-infra      # installatiescenario's (opnieuw installeren, context, origineel)
make acceptance     # install + e2e + e2e-infra, zoals de acceptance stage in CI
make uninstall      # namespace banksim-mse verwijderen
```

## Werken met Claude Code

De werkwijze is als Claude Code-skill vastgelegd. Zo volgt elke sessie dezelfde procedure, met stoppunten voor jou bij het scenario, bij elke ADR en bij elke commit.

- **[`.claude/skills/atdd/`](.claude/skills/atdd/README.md):** de algemene skill `atdd` met ATDD volgens Farley. Je kunt hem ook in andere projecten gebruiken.
- **[`CLAUDE.md`](CLAUDE.md):** het ATDD-profiel van dit project, met commando's, paden, doelomgeving, afspraken en valkuilen.

Start een iteratie met bijvoorbeeld `/atdd begin aan slice 1` of `/atdd volgend scenario`. Het [README van de skill](.claude/skills/atdd/README.md) legt uit wat de skill toevoegt, hoe een iteratie verloopt en hoe je hem in andere projecten inzet.

## Documentatie

| Document | Inhoud |
| --- | --- |
| [Werkwijze volgens Modern Software Engineering](doc/werkwijze-mse.md) | De volledige werkwijze, met voor elke slice:<br>- scenario's<br>- double loop<br>- vierlaags acceptatietestmodel<br>- ADR-proces en -backlog<br>- uitgewerkte iteraties<br>- infrastructuur als code<br>- traceerbaarheid naar het origineel<br>- Definition of Done |
| [ATDD in een team](doc/teamwerk-mse.md) | Hoe een team de werkwijze toepast volgens Farley: van epic naar scenario, werk verdelen per scenario in plaats van per laag, en de samenwerkingsmomenten |
| [Controle door de mens](doc/werkwijze-mse.md#101-controle-door-de-mens) | Wat je als mens nog wél en níet meer controleert bij code die AI via ATDD maakt: scenario, ADR, rapport en diff van de specs, verkennend testen per slice. De sabotages herhaalt PIT bij elke commit (ADR 0018) |
| [Archief](doc/archief) | De gearchiveerde controle-documenten van stap 0 tot en met slice 3; niet meer bijgehouden |
| [Skill `atdd`](.claude/skills/atdd/README.md) | Nut en gebruik van de ATDD-skill, ook in andere projecten |
| [`doc/adr/`](doc/adr) | Architecture Decision Records, ontstaan tijdens de bouw |
| [Originele BankSim](https://github.com/edwinbulter/banksim) | Het functioneel en technisch ontwerp en de gegenereerde implementatie waar dit project naar toe werkt |
