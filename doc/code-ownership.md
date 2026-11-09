# Code-ownership: wat je moet kennen om BankSim-MSE te supporten

Dit document beschrijft welke kennis een developer nodig heeft om de code van BankSim-MSE te kunnen supporten. Per onderwerp staat een zelftoets, zodat je kunt nagaan of je die kennis echt hebt.

## Wat code-ownership hier betekent

Eigenaar ben je niet omdat je de code zelf hebt geschreven. Eigenaar ben je als je hem kunt wijzigen, een storing kunt oplossen en een beslissing kunt verdedigen, ook zonder de AI die hem bouwde.

Dat betekent niet dat je elk detail kent. Abstractie is er juist om niet alles tegelijk in je hoofd te hoeven houden. Daarom verschilt per laag wat je moet weten:

| Laag | Voorbeelden | Wat je moet kennen |
| --- | --- | --- |
| **Protocol of library** | PKCE, de transacties van PostgreSQL, Spring Security | Wat het garandeert en onder welke voorwaarden, niet hoe het vanbinnen werkt |
| **Onze keuzes** | Tokens alleen in de BFF, nooit rood, de vier testlagen | Alles, inclusief waarom en welke alternatieven zijn afgewezen |
| **Gedrag in productie** | Een herstart, een verlopen sessie, een volle schijf | Hoe het faalt, hoe je dat merkt en wat je dan doet |

De kennis moet bij het team zitten. Niet iedereen hoeft alles te kunnen, maar elk onderwerp moet bij meer dan één persoon zitten.

Deze indeling is onze eigen interpretatie, geen citaat. De achtergrond staat in [werkwijze §10.2](werkwijze-mse.md#102-leren-door-de-mens): wie de code niet meer zelf schrijft, houdt juist de taken die de meeste kennis vragen.

**Verhouding tot andere documenten.** [Een story door de stack](story-door-de-stack.md) beschrijft de vorm van het systeem, [Kwaliteitscontroles](kwaliteitscontroles.md) de controles, en de skill `uitleg` legt op verzoek een onderdeel uit. Dit document toetst of je het kunt.

## Zo gebruik je de toets

1. Beantwoord de vraag eerst zelf, zonder AI en zonder de code te openen, hardop of op papier.
2. Klap dan het antwoord uit. Een antwoord dat je alleen herkent, telt niet.
3. Zit er een gat? Lees de bron bij het antwoord, of gebruik `/uitleg <onderwerp>`.

De vragen gaan over ontwerpkeuzes (waarom is het zo), fouten en weggevallen regels (wat gebeurt er dan), storingen (waar kijk je eerst) en wijzigingen (waar begin je en wat raakt het).

Er is geen score. Houd per onderwerp in een team bij wie het kan, in deze vorm:

| Onderwerp | Kan ik | Met hulp | Nog niet |
| --- | --- | --- | --- |
| 2. Inloggen, sessies en toegang | | | |

Sommige antwoorden zijn "dat is niet getest" of "dat bestaat nog niet". Dat is geen gat in de toets: weten wat er niet bewezen is, hoort bij eigenaarschap.

**Inhoud**

1. [Domein en specificatie](#1-domein-en-specificatie)
2. [Inloggen, sessies en toegang](#2-inloggen-sessies-en-toegang)
3. [Geld en grootboek](#3-geld-en-grootboek)
4. [Betalen: nooit rood, gelijktijdigheid en idempotentie](#4-betalen-nooit-rood-gelijktijdigheid-en-idempotentie)
5. [Tijd en rente](#5-tijd-en-rente)
6. [Database en schema](#6-database-en-schema)
7. [API-contract](#7-api-contract)
8. [Frontend](#8-frontend)
9. [Acceptatietests](#9-acceptatietests)
10. [Kwaliteit en vangrails](#10-kwaliteit-en-vangrails)
11. [Pipeline en versies](#11-pipeline-en-versies)
12. [Installatie en Kubernetes](#12-installatie-en-kubernetes)
13. [Testdata en demo-data](#13-testdata-en-demo-data)
14. [Storingen in productie](#14-storingen-in-productie)
15. [Werken met AI](#15-werken-met-ai)

---

## 1. Domein en specificatie

**Waarom dit ertoe doet.** De pipeline bewijst alleen wat in de scenario's staat. Wie het domein niet kent, kan een fout voorbeeld niet herkennen.

**Basiskennis:** betaalrekening en spaarrekening, IBAN, grootboek, Gegeven-Als-Dan.

**Dit moet je van ons systeem weten**
- De scenario's in `e2e/specs/` zijn de actuele beschrijving van het gedrag.
- Er zijn klanten en een beheerder. Een klant is wie een rekeninghouder is, een beheerder heeft een rol in Keycloak ([ADR 0015](adr/0015-rol-beheerder-in-het-token.md)).
- De beheerder leest alleen, en verandert alleen de simulatiedatum ([ADR 0038](adr/0038-de-beheerder-leest-via-eigen-endpoints-onder-api-beheer.md)).

**1.1 Waar kijk je als je wilt weten wat het systeem hoort te doen, en wat wint bij een verschil?**
<details><summary>Antwoord</summary>

In de scenario's in `e2e/specs/`, en voor de beslissingen in de ADR's. Die zijn de bron van de waarheid; bij een verschil tussen een beschrijvend document en de code wint de code.

*Bron:* [Een story door de stack](story-door-de-stack.md), Leeswijzer; skill `atdd`, §6.
</details>

**1.2 Wat gebeurt er als een klant betaalt naar een IBAN dat niet in zijn contacten staat? En met een naam die niet bij dat IBAN past?**
<details><summary>Antwoord</summary>

Allebei worden geweigerd: er is geen contact (`onbekend-contact`), of de naam past niet (`naam-past-niet`). Betalen kan dus alleen naar een contact met de juiste naam.

*Bron:* `e2e/specs/betalen.spec.ts`, `api/bank-api.yaml`.
</details>

**1.3 Jan typt het IBAN van Sanne in de URL van een rekening. Wat ziet hij, en waarom is dat hetzelfde als bij een IBAN dat niet bestaat?**
<details><summary>Antwoord</summary>

Hij ziet "Rekening is niet gevonden.": `bank-api` antwoordt met een 404, omdat de query filtert op de gebruikersnaam uit het token. Hetzelfde gebeurt als hij de API rechtstreeks aanroept. Een IBAN is geen geheim, en elke rekening heeft een eigen URL, dus dit kan iedereen proberen. Andermans, onbekende en ongeldige IBAN's geven alle drie een 404, zodat een klant niet kan afleiden of een IBAN bestaat.

*Bron:* [ADR 0023](adr/0023-eigenaarschap-in-de-query-andermans-iban-geeft-404.md), `e2e/specs/toegang.spec.ts`.
</details>

**1.4 Kan een beheerder namens een klant betalen?**
<details><summary>Antwoord</summary>

Nee. `POST /api/betalingen` boekt alleen vanaf een eigen rekening, en een beheerder heeft geen rekeningen. Via de API wordt het dus geweigerd, en op het scherm is er geen knop Betalen.

*Bron:* [ADR 0038](adr/0038-de-beheerder-leest-via-eigen-endpoints-onder-api-beheer.md).
</details>

---

## 2. Inloggen, sessies en toegang

**Waarom dit ertoe doet.** Dit is de ingewikkeldste flow van het systeem, en een fout is hier vaak onzichtbaar tot het misgaat.

**Basiskennis:** OAuth2 authorization code flow, PKCE, OpenID Connect, access token en JWT, audience en issuer, sessiecookie en de vlaggen `Secure`, `HttpOnly` en `SameSite`, CSRF, XSS.

**Dit moet je van ons systeem weten**
- De route van het inloggen, pijl voor pijl: [Een story door de stack, 2.3](story-door-de-stack.md#23-als-jan-logt-in).
- De BFF houdt de tokens en de sessie, allebei in zijn geheugen maar los van elkaar: de sessie in Tomcat, de tokens per gebruiker in `InMemoryOAuth2AuthorizedClientService`; `bank-api` is een resource server ([ADR 0008](adr/0008-bff-in-plaats-van-tokens-in-de-browser.md)).
- Elk endpoint heeft `@PreAuthorize` ([ADR 0022](adr/0022-toegang-per-endpoint-met-preauthorize.md)). Eigenaarschap staat in de query ([ADR 0023](adr/0023-eigenaarschap-in-de-query-andermans-iban-geeft-404.md)).

**2.1 Waarom krijgt de browser nooit de tokens?**
<details><summary>Antwoord</summary>

Tokens in de browser zijn bereikbaar voor JavaScript, dus een XSS-lek zou ze kunnen stelen. Daarom doet de BFF de flow, bewaart hij de tokens zelf, en krijgt de browser alleen een `HttpOnly`-sessiecookie. Dat heeft een nadeel: de browser stuurt een cookie automatisch mee, ook bij een verzoek dat een andere site start. Daarom moet elk verzoek dat iets wijzigt ook een CSRF-token meesturen, en dat kan alleen onze eigen app. Let op: geen test controleert dat er nooit een token in de browser komt; dat staat op de backlog.

*Bron:* [ADR 0008](adr/0008-bff-in-plaats-van-tokens-in-de-browser.md), [ADR 0016](adr/0016-csrf-in-de-bff-met-een-cookie.md), [backlog](werkwijze-mse.md#losse-backlogpunten).
</details>

**2.2 Welke beschermingen van het inloggen regelen Spring Security en Keycloak, welke regelen wij zelf, en welke daarvan kunnen wij met een wijziging ongemerkt kapotmaken?**
<details><summary>Antwoord</summary>

**Spring Security en Keycloak:** Keycloak controleert het wachtwoord en maakt de code. Spring Security maakt en controleert `state`, en wisselt de code in. Daar hebben wij geen eigen code voor.

**Onze configuratie:** de PKCE-eis, de toegestane redirect-URI en de brute-force-detectie in de realm-JSON; CSRF en de regels per pad in `BeveiligingConfig`; en `forward-headers-strategy`, waardoor de BFF weet dat hij achter HTTPS draait, wat nodig is voor de vlag `Secure` op het sessiecookie.

**Ongemerkt kapot te maken:** vooral de realm-instellingen en de cookievlaggen, want die hebben geen test. CSRF, de 401 zonder sessie en de routes zijn wel bewaakt, door `BffTests` en helm-unittest. Dat een verkeerde `state` wordt geweigerd, is op 3 november 2026 met de hand gezien; ook daarvoor staat een test op de backlog, met lage prioriteit, omdat wij die controle niet zelf hebben gebouwd.

*Bron:* `deploy/banksim-mse/files/realm-banksim-mse.json`, `BeveiligingConfig` en `application.yaml` van `bank-bff`, [backlog](werkwijze-mse.md#losse-backlogpunten).
</details>

**2.3 Inloggen werkt plotseling niet meer. Waar kijk je eerst?**
<details><summary>Antwoord</summary>

Bepaal eerst waar de flow stopt, want dat wijst het onderdeel aan:
- **Geen inlogscherm:** Keycloak of de ingress. Kijk of de pods draaien, en of `https://auth-mse.localtest.me/realms/banksim-mse/.well-known/openid-configuration` een 200 geeft (de rooktest van `install.sh`).
- **Melding op het inlogscherm:** wachtwoord of Keycloak zelf. `bruteForceProtected` staat aan, dus een gebruiker kan tijdelijk geblokkeerd zijn.
- **Terug bij de BFF met een fout (`?error`):** de BFF kon de code niet inwisselen. Kijk in de logs van `bank-bff` en `keycloak`. Een verschil tussen het client secret in het Secret `bank-bff` en dat in de realm hoort bij de verdachten.
- **Ingelogd, maar elke aanroep geeft een fout:** `bank-api` weigert het token, of de sessie is weg.

Controleer ook wat er als laatste is veranderd: `helm history`, en de K8s Job `keycloak-config`. Er zijn geen eigen logregels en geen metrics (zie [14](#14-storingen-in-productie)).

*Bron:* `deploy/scripts/install.sh`, `deploy/banksim-mse/files/realm-banksim-mse.json`, [ADR 0017](adr/0017-realm-wijzigingen-met-keycloak-config-cli.md).
</details>

**2.4 Wat gebeurt er met ingelogde klanten als Keycloak of de BFF herstart?**
<details><summary>Antwoord</summary>

**BFF:** de sessies staan in zijn geheugen en er is één replica, dus iedereen is zijn sessie kwijt. Dat geldt ook bij een upgrade die de BFF vervangt. De app krijgt een 401 en toont "Inloggen". Is de sessie bij Keycloak er nog, dan zal Keycloak waarschijnlijk niet opnieuw om het wachtwoord vragen.

**Keycloak:** nieuwe logins lukken niet tijdens de herstart. Bestaande sessies van de BFF blijven bestaan, en een access token is 5 minuten geldig, dus aanroepen kunnen eerst nog doorgaan. Daarna heeft de BFF Keycloak nodig.

Wat er in dat laatste geval precies gebeurt, is niet bewezen: geen scenario herstart Keycloak. Dat hoort bij de CFR Resilience.

*Bron:* [ADR 0008](adr/0008-bff-in-plaats-van-tokens-in-de-browser.md), `application.yaml` van `bank-bff`, `accessTokenLifespan` in de realm-JSON, [werkwijze 9.4](werkwijze-mse.md#94-hoe-de-infrastructuur-meegroeit-met-de-slices).
</details>

**2.5 Je haalt `pkce.code.challenge.method` weg uit de realm-JSON, of zet er een extra `*` bij `redirectUris`. Wat vangt dat?**
<details><summary>Antwoord</summary>

Waarschijnlijk niets. De BFF stuurt nog steeds PKCE, dus alles blijft werken, en `keycloak_test.yaml` controleert alleen of de juiste redirect-URI erin staat. Een `*` zou een aanvaller kunnen laten meekijken met de code. Dat Keycloak PKCE en de exacte redirect-URI nu afdwingt, is met de hand gezien. Dat een wijziging groen blijft, is afgeleid uit de tests en niet met sabotage aangetoond. Daarom staat dit op de backlog.

*Bron:* [backlog](werkwijze-mse.md#losse-backlogpunten), `deploy/banksim-mse/tests/keycloak_test.yaml`.
</details>

**2.6 Een verzoek geeft een 403. Wat zijn de twee mogelijke oorzaken?**
<details><summary>Antwoord</summary>

Het CSRF-token ontbreekt (bij alles behalve GET, HEAD en OPTIONS; de BFF weigert dan), of de rol ontbreekt (`@PreAuthorize` in `bank-api`). Zonder sessie of token krijg je juist een 401.

*Bron:* [ADR 0016](adr/0016-csrf-in-de-bff-met-een-cookie.md), [ADR 0022](adr/0022-toegang-per-endpoint-met-preauthorize.md).
</details>

**2.7 Wat gebeurt er als iemand de guard op `/beheer` in de browser omzeilt?**
<details><summary>Antwoord</summary>

Dan ziet hij hooguit een leeg scherm. De guard is alleen navigatie, een bordje en geen slot. Het slot zit in `bank-api`: een klant krijgt op `/api/beheer/**` altijd een 403.

*Bron:* [ADR 0037](adr/0037-de-frontend-kent-de-rol-via-api-ik.md).
</details>

**2.8 Je voegt een endpoint toe dat klantgegevens leest. Waar moet je aan denken?**
<details><summary>Antwoord</summary>

Eerst het contract. Daarna `@PreAuthorize`: zonder annotatie wordt de commit stage rood. De query moet de gebruikersnaam uit het token als parameter meekrijgen, en dat dwingt geen vangrail af. Dat bewaak je met een repositorytest en een 404-test op andermans data. Een endpoint voor de beheerder krijgt een eigen query en hoort onder `/api/beheer`.

*Bron:* [ADR 0022](adr/0022-toegang-per-endpoint-met-preauthorize.md), [ADR 0023](adr/0023-eigenaarschap-in-de-query-andermans-iban-geeft-404.md), [ADR 0034](adr/0034-contract-first-openapi-voor-bank-api.md), [ADR 0038](adr/0038-de-beheerder-leest-via-eigen-endpoints-onder-api-beheer.md).
</details>

---

## 3. Geld en grootboek

**Waarom dit ertoe doet.** Een fout in het geld is de duurste fout in een bank, en hij valt vaak pas later op.

**Basiskennis:** double-entry boekhouden, `BigDecimal`, afrondingswijzen, deferred constraints in PostgreSQL.

**Dit moet je van ons systeem weten**
- Een bedrag is `Bedrag` in Java, `numeric(15,2)` in de database en een string in JSON ([ADR 0010](adr/0010-bedragen-als-bigdecimal-en-string.md)).
- `LedgerService` is de enige plek die overboekingen boekt ([ADR 0024](adr/0024-grootboek-overboeking-met-twee-kanten.md)).
- Het saldo wordt berekend en niet opgeslagen: openingssaldo plus de transacties tot en met vandaag.

**3.1 Waarom zijn bedragen nergens een `double` en in JSON een string?**
<details><summary>Antwoord</summary>

Een `double` kan bedragen als 0,10 niet exact weergeven, dus er ontstaan afrondingsfouten. Ook de JSON-number wordt in JavaScript een drijvende komma; daarom is een bedrag in JSON een string, en rekent de frontend er niet mee. Vangrails bewaken dit in de backend (`GeenDrijvendeKommaTests`) en de frontend (ESLint).

*Bron:* [ADR 0010](adr/0010-bedragen-als-bigdecimal-en-string.md).
</details>

**3.2 Waarom heeft een overboeking twee kanten, en wie dwingt dat af?**
<details><summary>Antwoord</summary>

Zo verdwijnt er geen geld en ontstaat het ook niet: de som van de twee kanten is nul. Dat wordt op drie plekken afgedwongen: `Overboeking` in het domein kan geen andere vorm maken, ArchUnit laat alleen `LedgerService` schrijven, en een constraint trigger controleert bij de commit het aantal rijen, de twee rekeningen en de som.

*Bron:* [ADR 0024](adr/0024-grootboek-overboeking-met-twee-kanten.md), `V6__grootboek.sql`.
</details>

**3.3 Iemand schrijft met SQL één kant van een overboeking, met een `overboeking_id`. Wanneer gaat dat mis, en wat merk je ervan?**
<details><summary>Antwoord</summary>

Pas bij de commit, niet bij de INSERT: de constraint trigger is `DEFERRABLE INITIALLY DEFERRED`, omdat de twee kanten na elkaar worden geschreven. De INSERT lijkt dus te lukken, waarna de commit faalt en de hele transactie terugrolt. Let daarop in een test: zonder commit draait de controle niet.

*Bron:* [ADR 0024](adr/0024-grootboek-overboeking-met-twee-kanten.md), `V6__grootboek.sql`.
</details>

**3.4 Een transactie heeft geen `overboeking_id`. Is dat een fout?**
<details><summary>Antwoord</summary>

Nee. Dat is een eenzijdige beweging met de buitenwereld, zoals een salaris van een werkgever zonder rekening bij BankSim. De seed en de generator schrijven zulke rijen. De som-invariant geldt dus alleen voor overboekingen, niet voor alle saldi samen.

*Bron:* [ADR 0024](adr/0024-grootboek-overboeking-met-twee-kanten.md), [ADR 0041](adr/0041-de-generator-boekt-de-historie-samen-met-de-demo-klant.md).
</details>

**3.5 Wat bewaakt de trigger níet?**
<details><summary>Antwoord</summary>

De typen. Welk transactietype bij welke kant hoort, bepaalt het domein. Handmatige SQL in de database zou een verkeerde combinatie kunnen schrijven.

*Bron:* [ADR 0029](adr/0029-transactietype-per-kant-van-de-overboeking.md).
</details>

---

## 4. Betalen: nooit rood, gelijktijdigheid en idempotentie

**Waarom dit ertoe doet.** Dit soort fouten zie je niet in een gewone test: ze ontstaan pas bij toekomstige boekingen, gelijktijdige verzoeken of een herhaalde klik.

**Basiskennis:** READ COMMITTED, `SELECT … FOR UPDATE`, deadlocks en vaste lockvolgorde, `INSERT … ON CONFLICT`, idempotentie, property-based testing.

**Dit moet je van ons systeem weten**
- Nooit rood kijkt naar het laagste lopende saldo vanaf de uitvoerdatum ([ADR 0025](adr/0025-nooit-rood-minimum-lopend-saldo-vanaf-de-uitvoerdatum.md)).
- `LedgerService.boek` vergrendelt, leest, controleert, schrijft en publiceert, in die volgorde ([ADR 0026](adr/0026-locking-van-rekeningen-in-iban-volgorde.md)).
- `Idempotency-Key` is verplicht bij betalen ([ADR 0027](adr/0027-idempotency-key-bij-betalen.md)).

**4.1 Waarom kan een betaling worden geweigerd terwijl het saldo van vandaag groot genoeg is?**
<details><summary>Antwoord</summary>

Er kunnen al latere boekingen in de database staan, zoals een huur op een latere datum. Zou die de rekening rood maken, dan wordt de betaling nu al geweigerd. Daarom noemt de melding "nu of op een latere datum".

*Bron:* [ADR 0025](adr/0025-nooit-rood-minimum-lopend-saldo-vanaf-de-uitvoerdatum.md).
</details>

**4.2 Wat gebeurt er als je het vergrendelen uit `LedgerService.boek` weghaalt?**
<details><summary>Antwoord</summary>

Twee gelijktijdige boekingen lezen dan hetzelfde saldo en kunnen samen de rekening rood maken. Dat is ook echt gezien: rekeningen eindigden op -€ 20,00 en -€ 50,00, terwijl het totaal klopte. `GelijktijdigeOverboekingenTests` vangt dit.

*Bron:* [ADR 0026](adr/0026-locking-van-rekeningen-in-iban-volgorde.md).
</details>

**4.3 Waarom staat `ORDER BY iban` in de lock-query?**
<details><summary>Antwoord</summary>

Als A aan B betaalt terwijl B aan A betaalt, en elk zijn eigen rekening eerst vergrendelt, ontstaat een deadlock. Met een vaste volgorde kan dat niet. De volgorde staat in de query, zodat een aanroeper hem niet kan vergeten.

*Bron:* [ADR 0026](adr/0026-locking-van-rekeningen-in-iban-volgorde.md).
</details>

**4.4 Waarom hoort de foreign key van `rentebijschrijving` bij het ontwerp van de gelijktijdigheid?**
<details><summary>Antwoord</summary>

Hij houdt een opname of inleg en de maandafsluiting op dezelfde spaarrekening uit elkaar. Daarom blijft het vastleggen van de maand de eerste stap van `MaandRente.boek`. Wie de foreign key weghaalt of iets vóór dat vastleggen zet, krijgt een rode `GelijktijdigeRenteTests`.

*Bron:* [ADR 0039](adr/0039-de-foreign-key-houdt-opname-en-afsluiting-uit-elkaar.md).
</details>

**4.5 Een klant klikt twee keer op Bevestigen. Wat gebeurt er, stap voor stap?**
<details><summary>Antwoord</summary>

Het scherm maakt één sleutel, dus beide verzoeken sturen dezelfde. `BetaalService` doet als eerste `INSERT … ON CONFLICT DO NOTHING`. De tweede poging wacht op de unieke index tot de eerste klaar is en leest daarna het bewaarde antwoord. Er is één overboeking, en de klant krijgt twee keer een 201.

*Bron:* [ADR 0027](adr/0027-idempotency-key-bij-betalen.md).
</details>

**4.6 De eerste poging wordt geweigerd omdat het saldo te laag is. Wat gebeurt er als de klant het later opnieuw probeert met dezelfde sleutel? En met dezelfde sleutel maar een ander bedrag?**
<details><summary>Antwoord</summary>

**Geweigerd:** de transactie rolt terug, ook de sleutel, dus een nieuwe poging wordt opnieuw beoordeeld. **Ander bedrag:** dat geeft een 422 met `sleutel-al-gebruikt`, want de hash van het verzoek verschilt.

*Bron:* [ADR 0027](adr/0027-idempotency-key-bij-betalen.md).
</details>

---

## 5. Tijd en rente

**Waarom dit ertoe doet.** De simulatiedatum geldt voor de hele bank, en de rente hangt af van verleden en toekomst. Een fout hier lijkt vaak op een fout ergens anders.

**Basiskennis:** tijdzones en zomertijd, `java.time.Clock`, Spring `@Scheduled` en `@EventListener`.

**Dit moet je van ons systeem weten**
- `SimulationClock.vandaag()` is "vandaag" voor de bank ([ADR 0013](adr/0013-simulationclock-vervangt-de-systeemtijd.md)).
- De rente wordt per maand bijgeschreven via `LedgerService` ([ADR 0028](adr/0028-rente-bijschrijven-als-maandafsluiting.md)).
- Een boeking in een afgesloten maand leidt tot een rentecorrectie, via een event ([ADR 0030](adr/0030-rentecorrectie-via-een-event-van-het-grootboek.md)).

**5.1 Waarom mag code nergens `LocalDate.now()` zonder `Clock` gebruiken?**
<details><summary>Antwoord</summary>

Dan negeert die code de simulatiedatum. Bovendien draait de pod in UTC: zonder expliciete zone zou tussen middernacht en 2 uur de verkeerde dag gelden. `GeenSysteemtijdTests` is de vangrail.

*Bron:* [ADR 0013](adr/0013-simulationclock-vervangt-de-systeemtijd.md).
</details>

**5.2 Saldi en transacties kloppen plotseling niet meer, voor iedereen. Waar kijk je als eerste?**
<details><summary>Antwoord</summary>

Bij de simulatiedatum: tabel `instelling` in database `bank`, of het beheerscherm. Blijft er een datum staan, bijvoorbeeld na een afgebroken testrun, dan zijn latere transacties onzichtbaar. Wissen, niet op vandaag zetten: anders bevriest de bank op die dag.

*Bron:* [ADR 0014](adr/0014-scenarios-met-simulatiedatum-serieel.md), [ADR 0013](adr/0013-simulationclock-vervangt-de-systeemtijd.md), `CLAUDE.md`.
</details>

**5.3 De beheerder zet de datum terug. Verdwijnt de bijgeschreven rente dan?**
<details><summary>Antwoord</summary>

Nee, er wordt niets gewist. Rente na de simulatiedatum is alleen onzichtbaar, zoals elke latere transactie.

*Bron:* [ADR 0028](adr/0028-rente-bijschrijven-als-maandafsluiting.md).
</details>

**5.4 Wanneer draait de maandafsluiting, en wat als hij een nacht niet draait?**
<details><summary>Antwoord</summary>

Elke nacht om 00:05 in Europe/Amsterdam, en na het zetten en wissen van de simulatiedatum. De afsluiting boekt elke afgesloten maand die nog geen rente heeft, dus de volgende run haalt een gemiste maand in. De tabel `rentebijschrijving` voorkomt dat er dubbel wordt geboekt. Een mislukte nachtelijke run merkt nu niemand: er is geen melding (zie [14](#14-storingen-in-productie)).

*Bron:* [ADR 0028](adr/0028-rente-bijschrijven-als-maandafsluiting.md), `NachtelijkeAfsluiting`.
</details>

**5.5 Waarom kan een opname worden geweigerd door de correcties die ze zelf veroorzaakt?**
<details><summary>Antwoord</summary>

Een opname in het verleden verlaagt de rente van latere maanden. De correcties lopen ook via `LedgerService`, in dezelfde transactie, dus ook daarop geldt nooit rood. Maakt een correctie de rekening rood, dan rolt alles terug. De melding is gewoon "saldo ontoereikend".

*Bron:* [ADR 0036](adr/0036-nooit-rood-geldt-ook-voor-rentecorrecties.md), [ADR 0030](adr/0030-rentecorrectie-via-een-event-van-het-grootboek.md).
</details>

**5.6 Je leest `LedgerService.boek` en ziet geen rente-code. Waarom kan je wijziging toch de rente raken?**
<details><summary>Antwoord</summary>

`boek` publiceert `OverboekingGeboekt`, en `RenteService` luistert daarnaar, synchroon en in dezelfde transactie. Elke boeking op een spaarrekening leidt zo tot een herberekening. Het grootboek kent de rente-code niet; dat bewaakt `GrootboekKentRenteNietTests`. Een event is onzichtbaarder dan een aanroep, en dat is de prijs van deze keuze.

*Bron:* [ADR 0030](adr/0030-rentecorrectie-via-een-event-van-het-grootboek.md).
</details>

**5.7 Waarom mag de renterekening wel negatief worden?**
<details><summary>Antwoord</summary>

Het is een interne rekening van de bank (soort `intern`), en de bank betaalt de rente. Nooit rood geldt alleen voor betaal- en spaarrekeningen.

*Bron:* [ADR 0028](adr/0028-rente-bijschrijven-als-maandafsluiting.md).
</details>

---

## 6. Database en schema

**Waarom dit ertoe doet.** Een migratie kun je niet zomaar terugdraaien, en de data overleeft elke versie van de code.

**Basiskennis:** SQL, indexen, Flyway-migraties en callbacks, rechten in PostgreSQL, keyset-paginering, HMAC.

**Dit moet je van ons systeem weten**
- Eén PostgreSQL met de databases `bank` en `keycloak` ([ADR 0009](adr/0009-postgresql-flyway-en-jdbcclient.md)).
- Flyway draait bij het starten van `bank-api`.
- `JdbcClient` met expliciete SQL, geen JPA.

**6.1 Je past een Flyway-migratie aan die al is uitgevoerd. Wat gebeurt er, en wat doe je in plaats daarvan?**
<details><summary>Antwoord</summary>

Op een bestaande database klopt de controlesom dan niet meer, en start `bank-api` niet, terwijl een verse database zoals in CI er niets van merkt. Schrijf daarom altijd een nieuwe migratie.

*Bron:* basiskennis van Flyway; `backend/bank-api/src/main/resources/db/migration`.
</details>

**6.2 Mag `bank-seed` een rij wijzigen of wissen?**
<details><summary>Antwoord</summary>

Dat mag niet: de gebruiker `bank_seed` heeft alleen SELECT en INSERT op de tabellen van `bank-api`. Daarom kan de seed geen bestaande data aanpassen, en is opnieuw genereren alleen mogelijk op een verse installatie.

*Bron:* [ADR 0003](adr/0003-testdata-via-seed-cli.md), [ADR 0041](adr/0041-de-generator-boekt-de-historie-samen-met-de-demo-klant.md).
</details>

**6.3 Waarom kun je in de transactielijst niet naar pagina 7 springen, en wat krijg je daarvoor terug?**
<details><summary>Antwoord</summary>

Omdat de lijst keyset-paginering gebruikt, met een cursor. Daardoor blijft "Toon meer" correct als er een transactie bijkomt, en ook bij een grens midden in een dag. De schermen hebben geen paginanummers nodig.

*Bron:* [ADR 0011](adr/0011-keyset-paginering-met-cursor.md), [ADR 0035](adr/0035-binnen-een-dag-sorteren-op-tijdstip.md).
</details>

**6.4 Waarom heeft de cursor een HMAC, en wat merkt een klant als de sleutel wordt vervangen?**
<details><summary>Antwoord</summary>

De cursor komt van de client. Met de HMAC geeft een aangepaste cursor, of een cursor van een andere rekening, een 400. Wordt de sleutel vervangen, dan zijn open cursors ongeldig en opent de klant de rekening opnieuw. Zonder sleutel, of met minder dan 32 tekens, start `bank-api` niet.

*Bron:* [ADR 0011](adr/0011-keyset-paginering-met-cursor.md).
</details>

**6.5 Wat kan PIT niet zien aan een filter als `datum <= :vandaag`?**
<details><summary>Antwoord</summary>

PIT muteert Java-bytecode, geen SQL-strings. Zo'n filter is alleen gedekt doordat de test eerst rood was, door repositorytests met Testcontainers en door de acceptatietests.

*Bron:* [ADR 0018](adr/0018-mutation-testing-met-pit.md).
</details>

---

## 7. API-contract

**Waarom dit ertoe doet.** Het contract houdt de frontend en de backend bij elkaar. Zonder contract test elke kant tegen zijn eigen aanname.

**Basiskennis:** OpenAPI, probleemberichten (RFC 9457), HTTP-statuscodes.

**Dit moet je van ons systeem weten**
- `api/bank-api.yaml` is de bron voor de vorm van de API ([ADR 0034](adr/0034-contract-first-openapi-voor-bank-api.md)).
- De routes van de BFF zelf (`/oauth2`, `/login`, `/logout`) staan er niet in.

**7.1 Je voegt een field toe aan een antwoord van `bank-api`. In welke volgorde werk je, en wat faalt als je iets vergeet?**
<details><summary>Antwoord</summary>

1. Pas `api/bank-api.yaml` aan.
2. Draai `npm --prefix api run types` en commit `frontend/src/app/api/bank-api.d.ts` mee. Vergeet je dat, dan faalt de frontendjob.
3. Pas de code aan. Tot die tijd falen de controllertests, omdat het antwoord niet in het contract past. Objecten hebben `additionalProperties: false`, dus ook een extra field valt op.

*Bron:* [ADR 0034](adr/0034-contract-first-openapi-voor-bank-api.md), `CLAUDE.md`.
</details>

**7.2 Waarom is de specificatie OpenAPI 3.0.3, en niet 3.1?**
<details><summary>Antwoord</summary>

Met 3.1 slaat de validator in `bank-api` de schema's stil over, en blijft alles groen. Een controle die niets doet, valt niet op. Een overstap vraagt dus eerst een spike en een sabotage die rood moet worden.

*Bron:* [ADR 0034](adr/0034-contract-first-openapi-voor-bank-api.md), `CLAUDE.md`.
</details>

**7.3 Wat controleren de controllertests aan het contract níet?**
<details><summary>Antwoord</summary>

De verzoeken: alleen de antwoorden worden gecontroleerd, omdat de tests met opzet ook ongeldige verzoeken sturen. De vorm van een geldig verzoek bewaakt de typecheck van de frontend. De drivers in `e2e` hebben nog hun eigen types. Een foutantwoord mag zonder body.

*Bron:* [ADR 0034](adr/0034-contract-first-openapi-voor-bank-api.md).
</details>

**7.4 Wat doet de BFF met `/api/ik` als er geen sessie is, en als er wel een is?**
<details><summary>Antwoord</summary>

Zonder sessie antwoordt de BFF zelf met een 401, en de aanvraag bereikt `bank-api` niet. Met een sessie stuurt hij hem door met het access token en zonder de cookies van de browser. Het endpoint zelf staat in `bank-api`.

*Bron:* [Een story door de stack, 2.3](story-door-de-stack.md#23-als-jan-logt-in), `BeveiligingConfig` en `ApiRoute` in `bank-bff`.
</details>

---

## 8. Frontend

**Waarom dit ertoe doet.** Hier komen de regels over bedragen, sessies en dubbel verzenden bij de gebruiker terecht.

**Basiskennis:** Angular met router, guards en interceptors, signals, RxJS, Vitest, strenge typecontrole.

**Dit moet je van ons systeem weten**
- Elk scherm heeft een eigen route en laadt zijn eigen gegevens ([ADR 0012](adr/0012-navigatie-met-de-angular-router.md)).
- Een 401 op welk verzoek dan ook betekent: opnieuw inloggen (`sessie.ts`).
- De frontend kent de rol alleen voor de weergave ([ADR 0037](adr/0037-de-frontend-kent-de-rol-via-api-ik.md)).

**8.1 Je wilt twee bedragen optellen in de frontend. Mag dat?**
<details><summary>Antwoord</summary>

Niet zomaar. De frontend rekent nu niet: hij zet alleen de string om voor de weergave. ESLint verbiedt `parseFloat`, `parseInt`, `Number()` en de unaire `+`. Rekenen komt pas als een scenario erom vraagt, met `big.js` en een eigen ADR.

*Bron:* [ADR 0010](adr/0010-bedragen-als-bigdecimal-en-string.md).
</details>

**8.2 Waarom wordt de `Idempotency-Key` gemaakt bij het openen van het controlescherm, en niet bij de klik?**
<details><summary>Antwoord</summary>

Een sleutel per klik zou een dubbelklik twee sleutels geven, en dus twee betalingen. Eén sleutel per controlescherm betekent dat een dubbelklik dezelfde sleutel stuurt.

*Bron:* [ADR 0027](adr/0027-idempotency-key-bij-betalen.md), `betalen-scherm.ts`.
</details>

**8.3 Waarom eist `navigatie.spec.ts` dat de terugknop het overzicht toont zonder dat de app opnieuw laadt?**
<details><summary>Antwoord</summary>

Een herstart van de app komt ook op het overzicht uit. Zonder die eis bleef het scenario dus groen, ook als de terugknop niet goed werkte. Met de eis maakt een sabotage met `replaceUrl` het scenario rood.

*Bron:* [ADR 0012](adr/0012-navigatie-met-de-angular-router.md).
</details>

**8.4 Je voegt iets toe aan een scherm. Wat moet je bedenken bij het scenario?**
<details><summary>Antwoord</summary>

Of het `@ui` nodig heeft. Firefox en WebKit draaien alleen scenario's met `@ui` of `@rook`. Zonder die tag ziet alleen Chromium het, en vind je een browserfout pas in een verkennende sessie.

*Bron:* [ADR 0019](adr/0019-firefox-en-webkit-alleen-ui-en-rook.md).
</details>

---

## 9. Acceptatietests

**Waarom dit ertoe doet.** De scenario's zijn de specificatie. Wie niet weet wat ze bewijzen, weet ook niet wat een groene pipeline betekent.

**Basiskennis:** ATDD en de double loop, Playwright, interne DSL, test doubles.

**Dit moet je van ons systeem weten**
- Vier lagen: spec, DSL, driver en systeem (skill `atdd`, §3).
- Elk scenario maakt zijn eigen data via aliassen ([ADR 0005](adr/0005-acceptatietests-in-playwright-met-interne-dsl.md), [ADR 0003](adr/0003-testdata-via-seed-cli.md)).
- Scenario's met `@simulatiedatum` draaien serieel, na de rest ([ADR 0014](adr/0014-scenarios-met-simulatiedatum-serieel.md)).

**9.1 Wat mag een driver wel controleren, en wat niet?**
<details><summary>Antwoord</summary>

Een driver mag wachten en het protocol controleren, zoals de status en de vorm van een antwoord dat hij moet lezen. Een uitkomst die per scenario kan verschillen, geeft hij terug, en de DSL beweert erover. Een spec spreekt alleen de DSL; dat controleert `lagen-check.sh`. Het onderscheid tussen protocol en uitkomst is niet automatisch te controleren, dat blijft een punt voor de review.

*Bron:* [ADR 0031](adr/0031-drivers-wachten-en-controleren-het-protocol-de-dsl-beweert.md).
</details>

**9.2 Waarom heeft de simulatiedatum een aparte behandeling, terwijl alle andere data is geïsoleerd?**
<details><summary>Antwoord</summary>

Aliassen isoleren klanten, maar de datum geldt voor de hele bank. Parallel zouden scenario's elkaars datum veranderen. Daarom draaien ze serieel, na de andere, en wist de fixture de datum daarna, ook als het scenario faalde.

*Bron:* [ADR 0014](adr/0014-scenarios-met-simulatiedatum-serieel.md).
</details>

**9.3 Een nieuw scenario is meteen groen. Wat doe je?**
<details><summary>Antwoord</summary>

Je vertrouwt het nog niet: een scenario dat nooit rood was, bewijst niets. Maak een sabotage, en laat zien dat het scenario om de goede reden faalt. Een voorbeeld: `Een upgrade laat de demo-data ongemoeid` bestond met bestaande code, en is met twee sabotages rood gezien. Daarbij bleek dat een ouder scenario de wisfout niet ving.

*Bron:* skill `atdd`, §2 en §4; `git log` van `e2e/specs/infra/huishoudens.spec.ts`.
</details>

**9.4 `make e2e` is groen, maar je wijziging lijkt niet te werken, of andersom. Wat controleer je eerst?**
<details><summary>Antwoord</summary>

De versie. `make e2e` test wat er op dat moment geïnstalleerd is. Controleer `https://bank-mse.localtest.me/api/versie` of de voettekst, en gebruik na een codewijziging `make acceptance`.

*Bron:* `CLAUDE.md`.
</details>

**9.5 Waarom draai je nooit meerdere `simulatiedatum-*`-projecten tegelijk met `--no-deps`?**
<details><summary>Antwoord</summary>

Dan valt de keten weg, draaien ze parallel en wissen ze elkaars datum. Dat geeft valse fouten.

*Bron:* `CLAUDE.md`.
</details>

**9.6 Wat kunnen de lokale installatiescenario's niet bewijzen?**
<details><summary>Antwoord</summary>

Alles met `@destructief`: die draaien alleen in het CI-cluster, zoals upgraden en verwijderen. Het scenario over het origineel wordt lokaal overgeslagen als namespace `banksim` ontbreekt. Een wijziging daaraan zie je dus pas in de pipeline, of op een tijdelijke branch.

*Bron:* `e2e/playwright.config.ts`, `CLAUDE.md`.
</details>

---

## 10. Kwaliteit en vangrails

**Waarom dit ertoe doet.** Vangrails maken ontwerpregels afdwingbaar. Je moet weten welke fout ze vangen, en welke niet.

**Basiskennis:** mutation testing, ArchUnit, statische analyse.

**Dit moet je van ons systeem weten**
- De lijst met vangrails en hun tests staat in het profiel in `CLAUDE.md`.
- Het overzicht van alle soorten controles staat in [Kwaliteitscontroles](kwaliteitscontroles.md).

**10.1 De mutatiescore is groen. Wat weet je dan niet?**
<details><summary>Antwoord</summary>

Of er nieuwe levende mutanten zijn. De drempel van 95% laat er per module een of twee door. Daarom hoort het bekijken van levende mutanten bij de controle door de mens. Klassen `*Config` en `*Application` vallen buiten de meting, en SQL, YAML, Helm en Angular muteert PIT niet.

*Bron:* [ADR 0018](adr/0018-mutation-testing-met-pit.md), [werkwijze §10.1](werkwijze-mse.md#101-controle-door-de-mens).
</details>

**10.2 Error Prone geeft een melding die volgens jou onterecht is. Wat doe je?**
<details><summary>Antwoord</summary>

Eerst kijken of je de code kunt verbeteren. Is de melding echt onterecht, dan mag `@SuppressWarnings` op de kleinste plek, met een commentaar waarom. Dat verzwakt een vangrail, dus het vraagt een akkoord en een regel in de ADR. Een controle helemaal uitzetten vraagt een nieuwe ADR.

*Bron:* [ADR 0033](adr/0033-statische-analyse-met-error-prone.md).
</details>

**10.3 Compileren werkt in IntelliJ, maar `make backend-test` faalt, of andersom. Waar kijk je eerst?**
<details><summary>Antwoord</summary>

Naar `backend/.mvn/jvm.config`. Error Prone heeft die JVM-opties nodig op JDK 21, en IntelliJ leest dat bestand niet.

*Bron:* [ADR 0033](adr/0033-statische-analyse-met-error-prone.md), `CLAUDE.md`.
</details>

**10.4 Een vangrail wordt rood na je wijziging. Mag je hem aanpassen?**
<details><summary>Antwoord</summary>

Niet om groen te worden. Een rode vangrail wijst op een ontwerpfout in de code. Verzwakken, uitzonderen of weghalen vraagt een akkoord en een ADR.

*Bron:* skill `atdd`, §1 en §2.
</details>

**10.5 Wat bewijst een vangrail die nog nooit rood is geweest?**
<details><summary>Antwoord</summary>

Niets. Daarom krijgt elke vangrail eenmalig een sabotage: tijdelijk code die de regel overtreedt, de melding laten zien, en de code weer weghalen. In `CLAUDE.md` staat per vangrail of dat is gebeurd.

*Bron:* skill `atdd`, §4.
</details>

---

## 11. Pipeline en versies

**Waarom dit ertoe doet.** De pipeline is de scheidsrechter. Je moet hem kunnen lezen en repareren.

**Basiskennis:** continuous delivery, GitHub Actions, kind.

**Dit moet je van ons systeem weten**
- Eerst de commit stage zonder cluster, daarna de acceptance stage in een tijdelijk kind-cluster ([ADR 0002](adr/0002-deployment-pipeline-en-kind.md)).
- PIT draait parallel aan de acceptance stage ([ADR 0032](adr/0032-mutatietests-parallel-aan-de-acceptance-stage.md)).

**11.1 Waarom bouwt de acceptance stage de images niet opnieuw?**
<details><summary>Antwoord</summary>

Zo wordt precies getest wat de commit stage heeft gebouwd. De images krijgen de commit als tag en gaan als artefact naar de volgende stage, met `install.sh --image-tag`.

*Bron:* [ADR 0002](adr/0002-deployment-pipeline-en-kind.md).
</details>

**11.2 De pipeline is rood. Wat doe je, en waar zoek je?**
<details><summary>Antwoord</summary>

Dat gaat voor al het andere werk. Zoek de job die faalde met `gh run view`, en lees de log via `gh api repos/edwinbulter/banksim-mse/actions/jobs/<job-id>/logs`. Bij de installatiejobs staan de clusterstatus en het Playwright-rapport als artefact of in de log.

*Bron:* `CLAUDE.md`, `.github/workflows/ci.yml`.
</details>

**11.3 Acceptatie is groen, PIT is rood. Is de commit dan goed?**
<details><summary>Antwoord</summary>

Nee, de pipeline is rood. De acceptance stage wacht niet op PIT, om tijd te besparen, maar een rode PIT-job maakt de run alsnog rood.

*Bron:* [ADR 0032](adr/0032-mutatietests-parallel-aan-de-acceptance-stage.md).
</details>

**11.4 Waarom kan iets lokaal groen zijn en in CI falen, of andersom?**
<details><summary>Antwoord</summary>

Drie bekende redenen. CI installeert altijd vers, dus een ontbrekend recht op een bestaande realm zie je daar niet. Destructieve scenario's draaien alleen in CI. En een CI-stap met de shell-driver heeft `BANKSIM_CONTEXT` nodig, terwijl die lokaal terugvalt op `kind-single-node`.

*Bron:* `CLAUDE.md`, [ADR 0017](adr/0017-realm-wijzigingen-met-keycloak-config-cli.md).
</details>

**11.5 Hoe weet je welke versie er draait?**
<details><summary>Antwoord</summary>

Met `/api/versie` of de voettekst. Dat is de image-tag: de korte commit-hash, eventueel met `-dirty-<tijdstempel>`. Die `-dirty` betekent dat er bij het bouwen niet-gecommitte wijzigingen waren in `backend` of `frontend`. Een wijziging in `e2e` of `deploy` zie je dus niet aan de tag.

*Bron:* `CLAUDE.md`, `deploy/scripts/install.sh`.
</details>

---

## 12. Installatie en Kubernetes

**Waarom dit ertoe doet.** Een verkeerde handeling hier kan data wissen, of het origineel raken.

**Basiskennis:** Helm, hooks en `helm history`; Deployment, StatefulSet, PVC, Secret, Ingress, Job; `kubectl get`, `logs` en `describe`.

**Dit moet je van ons systeem weten**
- Alles staat in namespace `banksim-mse`, op context `kind-single-node`, zonder cluster-brede resources ([ADR 0004](adr/0004-installatie-in-namespace-banksim-mse.md)).
- `install.sh` maakt de Secrets één keer aan ([ADR 0009](adr/0009-postgresql-flyway-en-jdbcclient.md)).
- Wijzigen gaat alleen via de chart en de scripts, nooit met `kubectl edit` of `kubectl apply`.

**12.1 Wat gebeurt er als je één Secret met een wachtwoord verwijdert en opnieuw installeert?**
<details><summary>Antwoord</summary>

`install.sh` maakt dan een nieuw wachtwoord, maar PostgreSQL zet wachtwoorden alleen bij de eerste start op een lege PVC. De app en de database hebben dan verschillende wachtwoorden. Gebruik daarom `make uninstall`, dat de hele namespace met de PVC weghaalt, en dus ook de data.

*Bron:* `CLAUDE.md`, [ADR 0009](adr/0009-postgresql-flyway-en-jdbcclient.md).
</details>

**12.2 Waarom wordt een realm-wijziging apart op je bestaande installatie getest?**
<details><summary>Antwoord</summary>

CI installeert altijd vers. Een recht dat ontbreekt op een bestaande realm, zie je daar dus niet. De K8s Job `keycloak-config` past de JSON ook op een bestaande realm toe; draai de scenario's daarom eerst op je bestaande installatie, vóór een `make uninstall`.

*Bron:* [ADR 0017](adr/0017-realm-wijzigingen-met-keycloak-config-cli.md).
</details>

**12.3 Wat gebeurt er als iemand iets met de hand wijzigt in de console van Keycloak?**
<details><summary>Antwoord</summary>

Staat de instelling in de JSON, dan wint de JSON bij de volgende installatie. Staat ze er niet in, dan blijft de wijziging staan, zonder dat Git het weet.

*Bron:* [ADR 0017](adr/0017-realm-wijzigingen-met-keycloak-config-cli.md).
</details>

**12.4 Je voegt een geheim toe aan de realm-JSON, en het komt niet goed aan in Keycloak. Waar kijk je?**
<details><summary>Antwoord</summary>

Eerst naar de vorm: een geheim staat er als `$(env:NAAM)`, niet als `${NAAM}`. De realm gaat via de K8s Job `keycloak-config`, en daar geldt de vorm van keycloak-config-cli, niet die van Keycloak zelf. Kijk daarna of de Job die variabele uit een Secret krijgt, en lees zijn log; faalt de Job, dan faalt ook de installatie.

*Bron:* [ADR 0017](adr/0017-realm-wijzigingen-met-keycloak-config-cli.md), `deploy/banksim-mse/templates/keycloak-config.yaml`.
</details>

**12.5 Waarom mag `make install` niet de vervanging zijn van de tests?**
<details><summary>Antwoord</summary>

`make install` bouwt de images met `-DskipTests`, en de rooktests controleren alleen of er een status 200 terugkomt. Draai dus eerst `make commit-stage`, en daarna `make acceptance`.

*Bron:* `CLAUDE.md`.
</details>

**12.6 Waarom gebruiken de scripts nooit de actieve kubectl-context?**
<details><summary>Antwoord</summary>

Zo kan een verkeerde actieve context niet per ongeluk een ander cluster raken. De scripts gebruiken `kind-single-node`; alleen `--context` wijkt af, en een onbekende context wordt geweigerd. Een installatiescenario bewaakt dit.

*Bron:* [ADR 0004](adr/0004-installatie-in-namespace-banksim-mse.md).
</details>

---

## 13. Testdata en demo-data

**Waarom dit ertoe doet.** De demo-data gaat buiten de domeinservices om de database in. De regels moet de generator dus zelf toepassen.

**Basiskennis:** seed-data, idempotente opdrachten, deterministische generatie.

**Dit moet je van ons systeem weten**
- `bank-seed` is een CLI die testdata maakt, per Playwright-worker als sessie ([ADR 0003](adr/0003-testdata-via-seed-cli.md), [ADR 0020](adr/0020-seed-als-sessie-per-worker.md)).
- De K8s Job `bank-demo` maakt na elke installatie en upgrade de 10 klanten en de beheerder ([ADR 0040](adr/0040-demo-data-via-een-job-met-bank-seed.md)).
- De wachtwoorden staan in het Secret `bank-demo`.

**13.1 Je breidt de generator uit, en na installeren verandert er niets. Waarom?**
<details><summary>Antwoord</summary>

Een demo-klant die al bestaat, wordt overgeslagen. De transacties horen bij het aanmaken van de klant, dus een uitbreiding bereikt een bestaande installatie niet. Gebruik `make uninstall` en installeer opnieuw; dat wist ook de lokale data. CI installeert altijd vers.

*Bron:* [ADR 0041](adr/0041-de-generator-boekt-de-historie-samen-met-de-demo-klant.md), `CLAUDE.md`.
</details>

**13.2 Wat gebeurt er als een nieuw profiel een rekening rood zou maken?**
<details><summary>Antwoord</summary>

De generator controleert elke klant met `NooitRoodRegel` en gooit een fout. Dan faalt de K8s Job, en daarmee de hele installatie. Dat is bewust: liever geen installatie dan een demo met verkeerde data. Op dit moment bewaakt dit alleen de betaalrekening.

*Bron:* [ADR 0041](adr/0041-de-generator-boekt-de-historie-samen-met-de-demo-klant.md), `Generator`.
</details>

**13.3 Waarom gaan de boekingen niet langs `LedgerService` en de API?**
<details><summary>Antwoord</summary>

Er is geen endpoint voor een salaris van buiten, en duizenden aanroepen zouden een installatie veel trager maken. Een eigen endpoint zou een test-only endpoint zijn. Daarom schrijft de generator rechtstreeks, als eenzijdige rijen, en past hij de domeinregel zelf toe.

*Bron:* [ADR 0041](adr/0041-de-generator-boekt-de-historie-samen-met-de-demo-klant.md), [ADR 0024](adr/0024-grootboek-overboeking-met-twee-kanten.md).
</details>

**13.4 Wat voorkomt een halve klant als de login in Keycloak mislukt?**
<details><summary>Antwoord</summary>

De rekeninghouder, rekeningen, transacties en login staan in één databasetransactie per klant, met de login als laatste. Mislukt de login, dan rolt de database terug, en de volgende installatie maakt de klant opnieuw. `DemoDataDatabaseTests` bewaakt dit.

*Bron:* [ADR 0041](adr/0041-de-generator-boekt-de-historie-samen-met-de-demo-klant.md).
</details>

**13.5 Waarom staat de datumgrens tot 31 december 2026, ook als die in de toekomst ligt?**
<details><summary>Antwoord</summary>

Dat vraagt het FO. Een boeking na vandaag staat al in de database, maar de klant ziet hem pas als de datum zo ver is. Daardoor kan nooit rood al nu een betaling weigeren vanwege een latere boeking. Daarom houdt de generator op elke betaalrekening een buffer aan van een kwart maandinkomen van dat jaar (`Generator.buffer`): een vaste boeking die eronder zou komen, krijgt eerst een opname van de spaarrekening, en een aankoop vervalt. Het scenario "Elke betaalrekening houdt een buffer van een kwart maandinkomen aan" bewaakt dit.

*Bron:* [ADR 0041](adr/0041-de-generator-boekt-de-historie-samen-met-de-demo-klant.md), [ADR 0025](adr/0025-nooit-rood-minimum-lopend-saldo-vanaf-de-uitvoerdatum.md).
</details>

**13.6 Waarom start het project `infra` per opdracht een eigen seed-pod, en de andere projecten niet?**
<details><summary>Antwoord</summary>

Een upgrade vervangt de installatie midden in de run, en een sessie zou dan nog de oude versie draaien. Bij de gewone projecten is een sessie per worker sneller, omdat alleen de eerste aanroep het opstarten betaalt.

*Bron:* [ADR 0020](adr/0020-seed-als-sessie-per-worker.md).
</details>

---

## 14. Storingen in productie

**Waarom dit ertoe doet.** Dit is het eerlijkste deel van de toets: hier blijkt wat het systeem nog niet kan. Er is nog geen productie-inzet, en de antwoorden laten zien wat daarvoor nog moet gebeuren.

**Basiskennis:** de drie pijlers van observability (logs, metrics en traces), probes, rolling update, backup en restore.

**Dit moet je van ons systeem weten**
- Eigen logregels, metrics en traces ontbreken. Je hebt `kubectl get`, `logs` en `events`, de versie via `/api/versie`, `helm history`, en de database.
- Elk onderdeel heeft één replica.
- Liveness en readiness staan op een aparte poort en controleren geen afhankelijkheden.

**14.1 Een klant meldt dat een betaling misging. Wat kun je nu terugvinden, en wat niet?**
<details><summary>Antwoord</summary>

De database vertelt wat er is geboekt: `overboeking`, `transactie` en `idempotency_sleutel`. Een geslaagde betaling is dus terug te vinden. Een geweigerde niet: die rolt terug, inclusief de sleutel, en de code schrijft geen eigen logregel. Er is ook geen correlatie-ID tussen de BFF en `bank-api`.

*Bron:* [ADR 0027](adr/0027-idempotency-key-bij-betalen.md); er staat geen logger in de hoofdcode van de backend.
</details>

**14.2 `bank-api` komt niet ready en start steeds opnieuw. Wat zijn je eerste verdachten?**
<details><summary>Antwoord</summary>

Kijk met `kubectl logs --previous` en `describe pod` naar de oorzaak. Bekende verdachten zijn: PostgreSQL is niet bereikbaar, want Flyway draait bij het starten; een migratie faalt; de cursorsleutel ontbreekt of is te kort; of het wachtwoord in het Secret past niet bij de database.

*Bron:* [ADR 0009](adr/0009-postgresql-flyway-en-jdbcclient.md), [ADR 0011](adr/0011-keyset-paginering-met-cursor.md).
</details>

**14.3 Wat merken klanten van een upgrade?**
<details><summary>Antwoord</summary>

Hun sessie verdwijnt, want die staat in het geheugen van de BFF, en ze moeten opnieuw inloggen. De data blijft staan; dat bewijzen `Een upgrade behoudt de data` en `Een upgrade laat de demo-data ongemoeid`. Met één replica zijn er tijdens de wissel ook mislukte verzoeken mogelijk; zero-downtime is nog niet gebouwd of getest. Een open cursor kan na een wijziging ook ongeldig worden.

*Bron:* [ADR 0008](adr/0008-bff-in-plaats-van-tokens-in-de-browser.md), `e2e/specs/infra/upgrade.spec.ts`, [werkwijze 9.4](werkwijze-mse.md#94-hoe-de-infrastructuur-meegroeit-met-de-slices).
</details>

**14.4 PostgreSQL herstart. Is de data veilig, en hoe weet je dat?**
<details><summary>Antwoord</summary>

De data staat op een PVC, dus dat zou zo moeten zijn, maar geen scenario toetst het: een upgrade herstart PostgreSQL niet. Dit staat op de backlog bij de CFR Resilience. Tijdens de herstart werken `bank-api` en Keycloak niet.

*Bron:* [werkwijze 9.4 en de losse backlogpunten](werkwijze-mse.md#losse-backlogpunten).
</details>

**14.5 De nieuwe versie blijkt fout. Hoe ga je terug?**
<details><summary>Antwoord</summary>

Daar is geen geteste procedure voor. De chart kan terug naar een oudere versie, maar een uitgevoerde Flyway-migratie gaat niet vanzelf terug, en de oude code moet dan met het nieuwe schema werken. Ook een backup en restore bestaan nog niet. Vóór een echte productie-inzet moet dit worden ontworpen en geoefend.

*Bron:* er staat geen rollback- of backupprocedure in `deploy/`, `scripts/` en de pipeline.
</details>

**14.6 Een betaling blijft hangen en de BFF geeft een time-out. Is het geld afgeschreven?**
<details><summary>Antwoord</summary>

Dat weet je niet zeker: de BFF wacht maximaal 5 seconden (read-timeout), terwijl `bank-api` nog kan doorgaan en committen. Daarom is de `Idempotency-Key` belangrijk: een nieuwe poging vanaf hetzelfde controlescherm gebruikt dezelfde sleutel en boekt niet dubbel. Controleer in de database of de overboeking er staat. Dit precieze geval heeft geen eigen scenario.

*Bron:* `application.yaml` van `bank-bff`, [ADR 0027](adr/0027-idempotency-key-bij-betalen.md).
</details>

**14.7 Waarom kun je `bank-bff` niet zomaar op twee replica's zetten?**
<details><summary>Antwoord</summary>

De sessies staan in het geheugen, dus een klant zou bij de andere replica uitgelogd lijken. De tokens staan los van de sessie, ook in het geheugen van één pod. Een sessie in PostgreSQL vraagt een eigen ADR en moet de tokens meenemen. Voor meer replica's van `bank-api` geldt iets vergelijkbaars: de migraties moeten dan naar een eigen Job.

*Bron:* [ADR 0008](adr/0008-bff-in-plaats-van-tokens-in-de-browser.md), [ADR 0009](adr/0009-postgresql-flyway-en-jdbcclient.md).
</details>

**14.8 Waarom zitten Keycloak en de database niet in de liveness- en readiness-probes?**
<details><summary>Antwoord</summary>

Bij een storing van Keycloak zou Kubernetes dan gezonde pods van de bank uit de lucht halen. Dat verergert de storing. Een hiërarchische gezondheidscontrole over alle onderdelen hoort los van de probes, en bestaat nog niet.

*Bron:* `deploy/banksim-mse/templates/bank-bff.yaml`, `application.yaml` van `bank-api` en `bank-bff`; de CFR Resilience in [werkwijze hoofdstuk 7](werkwijze-mse.md#cross-functional-requirements-als-slices).
</details>

---

## 15. Werken met AI

**Waarom dit ertoe doet.** De AI schrijft snel, dus de waarde van de mens zit in het beoordelen. Dat kun je alleen met de kennis uit de andere hoofdstukken.

**Basiskennis:** ATDD, en wat een test wel en niet bewijst.

**Dit moet je van ons systeem weten**
- De stoppunten en wat je dan doet: [werkwijze §10.1](werkwijze-mse.md#101-controle-door-de-mens).
- Leren is een apart doel: [werkwijze §10.2](werkwijze-mse.md#102-leren-door-de-mens).

**15.1 Welke drie beslissingen blijven altijd bij de mens?**
<details><summary>Antwoord</summary>

Het scenario met zijn verwachte waarden, de ADR-beslissing, en de toestemming om te committen en te pushen. De AI mag voorstellen, maar besluit niet.

*Bron:* skill `atdd`, §1 en §5.
</details>

**15.2 De AI meldt "groen" en vraagt om te committen. Waar kijk je naar, als je niet alle code leest?**
<details><summary>Antwoord</summary>

Naar het rapport: was rood om de goede reden, en wat is niet geverifieerd? Naar `git diff --stat`: zijn goedgekeurde specs of beweringen in de DSL veranderd zonder dat jij dat vroeg? Lees de diff van `e2e/specs/`. Na de push: is de pipeline groen, ook de mutatietests?

*Bron:* [werkwijze §10.1](werkwijze-mse.md#101-controle-door-de-mens).
</details>

**15.3 Wat is er verdacht als de AI in één stap een test en de code tegelijk verandert?**
<details><summary>Antwoord</summary>

Een test die meebeweegt met de code, bewijst niets. Eerst de falende test laten zien, dan de code. Hetzelfde geldt voor een losser gemaakte bewering, een uitgezette test of een nieuwe uitzondering op een vangrail.

*Bron:* skill `atdd`, §2.
</details>

**15.4 De AI stelt een nieuwe library voor als klein implementatiedetail. Wat doe je?**
<details><summary>Antwoord</summary>

Dat is geen detail, maar een ADR-kandidaat, met opties en gevolgen. Jij beslist en zet de status op Geaccepteerd.

*Bron:* skill `atdd`, §1 en §2; [werkwijze §10](werkwijze-mse.md#10-werken-met-ai-in-deze-lus).
</details>

**15.5 Hoe merk je dat een document of deze toets niet meer klopt met de code?**
<details><summary>Antwoord</summary>

Dat merk je niet vanzelf: beschrijvingen worden niet getest. Daarom wint de bron bij een verschil, en verwijst elk antwoord hier naar een bron. De actuele uitleg krijg je met `/uitleg`, die uit de code van dat moment leest.

*Bron:* [werkwijze §10.2](werkwijze-mse.md#102-leren-door-de-mens).
</details>

---

## Onderhoud

- **Bij een verschil wint de bron**: het scenario, de ADR, de chart of de code.
- **Een nieuwe of vervangen ADR** kan een vraag raken. Kijk dat na bij de ⛔-stap van die ADR.
- **Een opgelost "niet getest" of "bestaat nog niet"**, zoals in de hoofdstukken 2 en 14, pas je aan in dezelfde commit als de oplossing.
- **Vul de toets aan** als een storing iets liet zien wat hier niet in stond. Een storing is de beste bron voor een nieuwe vraag.
