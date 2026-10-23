# RekeningControllerTests: hoe de controllertest van het overzicht werkt

Bestand: [`RekeningControllerTests.java`](RekeningControllerTests.java)

## Doel

De test controleert het endpoint `GET /api/rekeningen` van `RekeningController`. Dat endpoint hoort bij story S1.1 en toont de rekeningen van de ingelogde klant. De test bekijkt vier dingen:

1. Een ingelogde klant krijgt zijn eigen rekeningen terug, met het saldo als string (ADR 0010).
2. Een andere klant ziet die rekeningen niet (de basis voor S6.1).
3. Het saldo geldt op "vandaag" volgens de `SimulationClock` (ADR 0013, slice 3).
4. Zonder token is het antwoord 401.

Het is een **slicetest**. Alleen de weblaag draait. Er komen geen database, geen Keycloak en geen echte server aan te pas.

## Opbouw

### 1. De Spring-context: `@WebMvcTest` + `@Import`

```java
@WebMvcTest(RekeningController.class)
@Import({BeveiligingConfig.class, IngelogdeGebruiker.class, RekeningControllerTests.Rekeningen.class})
```

- `@WebMvcTest(RekeningController.class)` start een kleine Spring-context. Daarin zitten Spring MVC, Jackson, Spring Security en alleen deze ene controller. Andere controllers, repositories en de datasource worden niet geladen.
- Met `@Import` komen de drie onderdelen die de controller nodig heeft er expliciet bij:

| Klasse | Waarom |
| --- | --- |
| `BeveiligingConfig` | De **echte** security-filterchain van `bank-api`: `/api/**` vereist authenticatie, stateless, met een JWT-resource server. Zonder deze klasse zou de test de standaardbeveiliging van Spring gebruiken en niet die van ons. |
| `IngelogdeGebruiker` | Leest `preferred_username` uit het JWT in de `SecurityContext`. Het is een `@Component` die `@WebMvcTest` niet zelf meescant. |
| `Rekeningen` (de geneste `@TestConfiguration`) | Levert een nep-`RekeningRepository` (zie hieronder) en een `SimulationClock` met de simulatiedatum 5 oktober 2026. |

### 2. `MockMvc`

```java
@Autowired MockMvc mvc;
```

`MockMvc` stuurt een request door de hele servlet-stack, inclusief de security-filters, maar zonder netwerk. Zo test je de URL-mapping, de beveiliging, de JSON-serialisatie en de statuscodes zoals een client ze zou zien.

### 3. `@MockitoBean JwtDecoder`

```java
@MockitoBean JwtDecoder jwtDecoder;
```

`BeveiligingConfig` zet de resource server aan, en die heeft een `JwtDecoder`-bean nodig. Normaal bouwt Spring die uit `jwk-set-uri`, maar die URL bestaat in de test niet. Daarom staat hier een mock, alleen om de context te laten starten. In deze tests wordt de mock **nooit aangeroepen**. Het waarom staat bij punt 5.

### 4. De nep-repository: een lambda, geen Mockito

```java
@Bean
RekeningRepository rekeningRepository() {
    return (gebruikersnaam, vandaag) -> {
        GEVRAAGD.add(vandaag);
        return "jan-a1-1".equals(gebruikersnaam)
                ? List.of(new Rekening(Soort.BETAAL, Iban.simBank(1L), Bedrag.van("1842.17")))
                : List.of();
    };
}
```

`RekeningRepository` heeft één methode, `vanGebruiker(String, LocalDate)`, en is daardoor met een lambda te implementeren. De lambda onthoudt in `GEVRAAGD` op welke datum de controller het saldo vroeg. De nep-repository kent precies één klant:

- `jan-a1-1` heeft één betaalrekening, `Iban.simBank(1L)` (= `NL94SIMB0000000001`), met een saldo van `1842.17`;
- iedere andere gebruiker heeft geen rekeningen.

Dit is een **test double op een echte grens**: de database. Die grens is apart getest in `JdbcRekeningRepositoryTests`, met Testcontainers en echte PostgreSQL. Het domein zelf (`Iban`, `Bedrag`) is wél echt.

### 5. Inloggen in de test: `jwt()`

```java
get("/api/rekeningen").with(jwt().jwt(token -> token.claim("preferred_username", "jan-a1-1")))
```

`jwt()` komt uit `spring-security-test`. Deze request-postprocessor zet direct een `JwtAuthenticationToken` in de `SecurityContext`, met een `Jwt` waarin de opgegeven claims staan. De stap "token decoderen en de handtekening controleren" wordt daarbij **overgeslagen**. Daarom wordt de gemockte `JwtDecoder` nooit gebruikt.

Daarna loopt het request zo:

```
MockMvc ──► security-filters (BeveiligingConfig: geauthenticeerd? ja)
        ──► RekeningController.rekeningen()
              ├─ IngelogdeGebruiker.gebruikersnaam()  → "jan-a1-1" (uit het Jwt)
              ├─ RekeningRepository.vanGebruiker(...)  → [Rekening(BETAAL, NL94…, 1842.17)]
              └─ map naar RekeningJson(soort, iban, saldo) — allemaal strings
        ──► Jackson → JSON-antwoord
```

## De vier tests

| Test | Request | Verwachting | Wat het bewijst |
| --- | --- | --- | --- |
| `geeftDeRekeningenVanDeIngelogdeGebruikerMetSaldoAlsString` | JWT met `preferred_username=jan-a1-1` | `200` en precies `[{"soort":"betaal","iban":"NL94SIMB0000000001","saldo":"1842.17"}]` | De gebruikersnaam komt uit het token; de mapping is juist; het saldo is een **string** (ADR 0010) |
| `geeftGeenRekeningenVanEenAndereGebruiker` | JWT met `preferred_username=piet-a1-2` | `200` en `[]` | De controller filtert op de ingelogde gebruiker en niet op een parameter die de client meestuurt |
| `vraagtHetSaldoOpDeDatumVanDeSimulationClock` | JWT met `preferred_username=jan-a1-1` | `GEVRAAGD` is `[2026-10-05]` | De controller vraagt het saldo op "vandaag" voor de bank, de simulatiedatum, en niet op de systeemdatum (ADR 0013). Of de query die datum gebruikt, toont `JdbcRekeningRepositoryTests`. |
| `zonderTokenIs401` | geen token | `401` | `BeveiligingConfig` beschermt `/api/rekeningen` |

### Waarom `JsonCompareMode.STRICT`

Een vergelijking in de standaardmodus (`LENIENT`) accepteert extra velden en een andere volgorde in arrays. Met `STRICT` faalt de test ook in deze gevallen:

- het antwoord bevat een extra veld, zoals een intern `id` of `rekeninghouderId`. Dat is meteen een controle dat er niets uitlekt;
- het saldo komt als getal (`1842.17`) en niet als string (`"1842.17"`). STRICT let op het JSON-type.

## Hoe de test `IngelogdeGebruiker` gebruikt

De test roept `IngelogdeGebruiker` **nergens zelf aan**. Hij zorgt er alleen voor dat de klasse in de Spring-context zit. Daarna gebruikt de controller hem, en de test controleert dat gebruik indirect via het antwoord.

### Stap 1: de test zet hem in de context

```java
@WebMvcTest(RekeningController.class)
@Import({BeveiligingConfig.class, IngelogdeGebruiker.class, RekeningControllerTests.Rekeningen.class})
```

`IngelogdeGebruiker` is een `@Component`. `@WebMvcTest` scant alleen de weblaag (controllers en dergelijke), dus zonder `@Import` zou hij ontbreken. De controller zou dan niet gemaakt kunnen worden en de context start niet ("No qualifying bean of type IngelogdeGebruiker").

Het is de **echte** klasse, geen nep. In de test komt alleen de repository van de nepkant.

### Stap 2: Spring geeft hem aan de controller

```java
RekeningController(RekeningRepository rekeningen, IngelogdeGebruiker gebruiker, SimulationClock klok) { … }
```

Net als bij de repository: Spring ziet dat de constructor een `IngelogdeGebruiker` vraagt, vindt de bean uit stap 1 en geeft die mee.

### Stap 3: de test zet een gebruiker klaar via `jwt()`

```java
mvc.perform(get("/api/rekeningen")
        .with(jwt().jwt(token -> token.claim("preferred_username", "jan-a1-1"))))
```

`jwt()` maakt een `Jwt` met de claim `preferred_username = "jan-a1-1"`. Dat gaat in een `JwtAuthenticationToken`, en daarmee wordt de **`SecurityContext`** voor dit request gevuld. Het gebeurt op precies dezelfde plek waar in productie het filter van de resource server het neerzet, nadat het token gecontroleerd is.

### Stap 4: de controller vraagt de naam op

```java
@GetMapping("/api/rekeningen")
List<RekeningJson> rekeningen() {
    return rekeningen.vanGebruiker(gebruiker.gebruikersnaam(), klok.vandaag())   // ◄── hier
            .stream().map(RekeningJson::van).toList();
}
```

### Stap 5: `IngelogdeGebruiker` leest de `SecurityContext`

```java
public String gebruikersnaam() {
    if (!(SecurityContextHolder.getContext().getAuthentication().getPrincipal() instanceof Jwt jwt)) {
        throw new IllegalStateException("Geen ingelogde gebruiker met een JWT");
    }
    return jwt.getClaimAsString("preferred_username");   // → "jan-a1-1"
}
```

`SecurityContextHolder` werkt per thread. Het request uit stap 3 wordt in dezelfde thread afgehandeld, dus `IngelogdeGebruiker` vindt het `Jwt` dat `jwt()` daar heeft neergezet en geeft `"jan-a1-1"` terug.

### Het hele pad

```
Test                         Spring / MockMvc                    Productiecode
────                         ────────────────                    ─────────────
@Import(IngelogdeGebruiker) ─► bean in context ─► constructor ─► RekeningController.gebruiker

jwt().claim(preferred_username,"jan-a1-1")
                            ─► SecurityContext { Jwt }
                                                   ▲
mvc.perform(GET /api/rekeningen) ─► filters ─► rekeningen()
                                                   │
                                     gebruiker.gebruikersnaam() ──┘ leest Jwt → "jan-a1-1"
                                                   │
                                     rekeningen.vanGebruiker("jan-a1-1")   (lambda)
                                                   │
                                     JSON ◄── 1 betaalrekening
```

### Waar de test het controleert

De test controleert `IngelogdeGebruiker` niet met een eigen assert. Dat gebeurt via het resultaat:

| Test | Als `IngelogdeGebruiker` goed werkt | Als hij de verkeerde claim leest (bijv. `sub`) |
| --- | --- | --- |
| `geeftDeRekeningenVanDeIngelogdeGebruiker…` | naam `jan-a1-1` → de lambda geeft de rekening → ✅ | naam is `null`/iets anders → `[]` → ❌ test faalt |
| `geeftGeenRekeningenVanEenAndereGebruiker` | naam `piet-a1-2` → `[]` → ✅ | – |
| `zonderTokenIs401` | wordt niet bereikt; de filterketen weigert eerder | – |

Een fout in `IngelogdeGebruiker` laat dus de eerste test falen. Zo test `RekeningControllerTests` controller, beveiligingsregels en `IngelogdeGebruiker` samen als één stukje weblaag, met alleen de database als nep.

## Wat deze test níét dekt (en waar dat wel gebeurt)

| Onderwerp | Waar getest |
| --- | --- |
| Echte tokenvalidatie: handtekening, issuer, audience `bank-api`, verlopen token | `beveiliging/TokenControleTests`, met echt ondertekende JWT's |
| De SQL-query en het schema | `JdbcRekeningRepositoryTests` (Testcontainers + Flyway) |
| Het hele pad browser → BFF → `bank-api` → PostgreSQL | het acceptatiescenario `e2e/specs/overzicht.spec.ts` |
| Geen `double`/`float` voor geld | `architectuur/GeenDrijvendeKommaTests` (ArchUnit) |

Elke test bewijst dus één laag, en samen dekken ze S1.1 van buiten naar binnen af.
