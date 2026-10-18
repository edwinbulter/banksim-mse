# BankSim-MSE

BankSim-MSE bouwt [BankSim](https://github.com/edwinbulter/banksim) opnieuw: een simulatie van internetbankieren voor 10 fictieve huishoudens met ongeveer 5 jaar transactiehistorie, waarbij een beheerder de simulatiedatum kan verzetten.

Het origineel is in één keer door AI gegenereerd uit een vooraf geschreven functioneel en technisch ontwerp. Dit project levert **dezelfde applicatie** op, met **dezelfde techstack**, maar volgens de werkwijze uit *Modern Software Engineering* van Dave Farley: leren in kleine stappen met snelle feedback, en complexiteit beheersen met modulair, testbaar ontwerp.

> **Status:** stap 0, het walking skeleton, is af. `bank-api` en `bank-web` tonen de versie op <https://bank-mse.localtest.me>, en het eerste acceptatiescenario slaagt via de API en in drie browsers, lokaal en in de [CI-pipeline](https://github.com/edwinbulter/banksim-mse/actions). De pipeline is eerst twee keer rood geweest en daarna groen. Volgende stap: slice 1, het Overzicht.

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
make acceptance     # install + e2e in één stap, zoals de acceptance stage in CI
make uninstall      # namespace banksim-mse verwijderen
```

## Documentatie

| Document | Inhoud |
| --- | --- |
| [Werkwijze volgens Modern Software Engineering](doc/werkwijze-mse.md) | De volledige werkwijze, met voor elke slice:<br>- scenario's<br>- double loop<br>- vierlaags acceptatietestmodel<br>- ADR-proces en -backlog<br>- uitgewerkte iteraties<br>- infrastructuur als code<br>- traceerbaarheid naar het origineel<br>- Definition of Done |
| [Stap 0 controleren](doc/stap-0-controleren.md) | Instructie om het walking skeleton zelf na te lopen: elke test groen en een keer rood zien, installeren, acceptatietests en veiligheid van de namespace |
| [`doc/adr/`](doc/adr) | Architecture Decision Records, ontstaan tijdens de bouw |
| [Originele BankSim](https://github.com/edwinbulter/banksim) | Het functioneel en technisch ontwerp en de gegenereerde implementatie waar dit project naar toe werkt |
