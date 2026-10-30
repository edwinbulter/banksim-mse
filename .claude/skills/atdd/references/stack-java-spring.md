# Stack: Java en Spring Boot (JUnit 5, MockMvc)

## Outside-in

Volg het scenario van buiten naar binnen. Elke laag heeft zijn eigen falende test.

1. **Controller:** een slicetest met `@WebMvcTest`. Vervang de service door een test double. Zo ontwerp je de interface van de service vanuit het gebruik.
2. **Service:** een gewone JUnit-test. Vervang de repository en de klok door test doubles.
3. **Domein:** pure JUnit, eventueel property-based met jqwik, zonder Spring. Dit is de plek voor regels, berekeningen en waardeobjecten.
4. **Repository:** een integratietest tegen een echte database, met Testcontainers. Gebruik geen in-memory vervanger als productie iets anders draait.

## Slicetest (Spring Boot 4)

```java
@WebMvcTest(controllers = VersieController.class, properties = "banksim.versie=1.2.3")
class VersieControllerTests {

    @Autowired
    MockMvc mvc;

    @Test
    void geeftDeIngesteldeVersie() throws Exception {
        mvc.perform(get("/api/versie"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.versie").value("1.2.3"));
    }
}
```

- In Spring Boot 4 staat `@WebMvcTest` in `org.springframework.boot.webmvc.test.autoconfigure`. Daarvoor is de dependency `spring-boot-starter-webmvc-test` nodig.
- Zet configuratiewaarden via `properties`. Zo test je dat de controller de waarde uit de configuratie gebruikt, en niet een hardgecodeerde waarde.
- **Rood om de goede reden:** laat de melding zien, bijvoorbeeld `JSON path "$.versie" expected:<1.2.3> but was:<…>`, of een 404 omdat de mapping nog niet bestaat.

## Fitness functions

- **ArchUnit** voor modulegrenzen. Voorbeelden: het domein hangt niet van Spring af; alleen `XService` schrijft naar Y; nergens wordt `LocalDate.now()` aangeroepen buiten de klok.
- **Concurrency-tests** voor invarianten onder parallelle aanroepen.

### Vangrails met ArchUnit (SKILL.md §4)

> Gedraaid in atdd-casus-1 (ArchUnit 1.5.1, Spring Boot 4.0.8): elke regel één keer rood gezien via sabotage, daarna groen.

```java
@AnalyzeClasses(packages = "nl.example.app", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTests {

    @ArchTest
    static final ArchRule layers = layeredArchitecture().consideringOnlyDependenciesInLayers()
            .layer("api").definedBy("..api..")
            .layer("application").definedBy("..application..")
            .layer("domain").definedBy("..domain..")
            .whereLayer("api").mayNotBeAccessedByAnyLayer()
            .whereLayer("application").mayOnlyBeAccessedByLayers("api")
            .whereLayer("domain").mayOnlyBeAccessedByLayers("api", "application")
            .withOptionalLayers(true);

    @ArchTest
    static final ArchRule domainKnowsNoFramework = noClasses().that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAnyPackage("org.springframework..", "jakarta..")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule noSynchronizedMethods = noMethods()
            .that().areDeclaredInClassesThat().resideInAnyPackage("..api..", "..application..", "..domain..")
            .should().haveModifier(JavaModifier.SYNCHRONIZED)
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule noSystemClock = noClasses()
            .should().callMethod(LocalDate.class, "now")
            .orShould().callMethod(LocalDateTime.class, "now")
            .orShould().callMethod(Instant.class, "now");

    @ArchTest
    static final ArchRule dtosStayInApi = noClasses().that().resideOutsideOfPackage("..api..")
            .should().dependOnClassesThat().resideInAPackage("..api..");

    @ArchTest
    static final ArchRule noCycles = slices().matching("nl.example.app.(*)..").should().beFreeOfCycles()
            .allowEmptyShould(true);
}
```

- **Een regel die geen klassen controleert, faalt** ("failed to check any classes", of "Layer 'domain' is empty"). Dat gebeurt zolang een laag nog leeg is. Zet de uitzondering per regel (`allowEmptyShould(true)`, `withOptionalLayers(true)`) alleen op de regels over één laag. Laat de regels over alle klassen streng (niet `archRule.failOnEmptyShould=false` globaal): dan wordt een verkeerd root-package meteen rood.
- **Noem de klasse `*Tests`.** Surefire draait standaard alleen `*Test`, `*Tests`, `Test*` en `*TestCase`; een klasse `ArchitectureRules` wordt stil overgeslagen.
- **`synchronized`-blokken ziet ArchUnit niet.** Het keyword op een methode is een modifier; een blok is alleen een `monitorenter` in de bytecode. Vul de regel aan met een gewone JUnit-test die de bronbestanden in `src/main/java` op `synchronized` scant.
- **Idempotentie** krijgt per mutatie een eigen unittest of scenario: dezelfde mutatie twee keer geeft dezelfde toestand als één keer.
- `callMethod(LocalDate.class, "now")` zonder parametertypes raakt alleen `now()`. `LocalDate.now(clock)` blijft toegestaan; zo dwing je de geïnjecteerde `Clock` af.
- **Java 21:** records voor waardeobjecten en commando's, sealed interfaces met pattern matching voor gevalsonderscheid. Een regel als "klassen in `domain` hebben geen setters" (`noMethods().that().haveNameStartingWith("set")`) kan dat bewaken.
- **Mutation testing** met PIT (`pitest-maven`), om te controleren of de tests echt iets vangen.

## Valkuilen (echt opgetreden)

- **Testcontainers met een image op digest:** `new PostgreSQLContainer("postgres:18.6-alpine@sha256:…")` faalt, omdat Testcontainers de naam niet als compatibel herkent. Gebruik `DockerImageName.parse("…@sha256:…").asCompatibleSubstituteFor("postgres")`.
- **Spring Cloud Gateway (server-webmvc) en de HTTP-client van de JDK:** de gateway stuurt de body van elk request gestreamd door, ook bij een GET zonder body. De JDK-client verstuurt dat als lege chunked body (`Transfer-Encoding: chunked`). Daarbij gooide hij in CI af en toe een `NullPointerException` in `Http1Exchange.requestMoreBody` ("bodySubscriber is null"); lokaal trad dat nooit op. Oplossing: een `ClientHttpRequestFactory`-bean met `BufferingClientHttpRequestFactory` om de `JdkClientHttpRequestFactory`. Een lege gebufferde body gaat dan helemaal niet mee. Test met een nep-backend dat een GET geen `Transfer-Encoding`-header heeft.
- **De JDK-client en h2c:** bij `http://` vraagt de client standaard om een upgrade naar HTTP/2 (`Upgrade: h2c`). Zet hem vast op HTTP/1.1 (`HttpClient.Version.HTTP_1_1`) als de backend HTTP/1.1 spreekt. Dit loste de `NullPointerException` hierboven níet op, al leek dat na één groene run wel zo.

- **`jwt()` uit spring-security-test slaat je eigen `JwtAuthenticationConverter` over.** Het geeft standaard alleen `SCOPE_`-authorities; rollen uit een claim (zoals `realm_access.roles`) worden niet omgezet. Een controllertest met `jwt().authorities(...)` bewijst dus niet dat de converter klopt. Test de converter met een echt ondertekend token tegen de `JwtDecoder` uit de configuratie, bijvoorbeeld met een eigen JWKS-endpoint.
- **CSRF voor een SPA (`csrf.spa()`, Spring Security 6.4+):** het token staat in cookie `XSRF-TOKEN` (niet HttpOnly) en moet terug als header `X-XSRF-TOKEN`. Een client zonder cookie krijgt bij een PUT, POST of DELETE een 403. Een API-client (zoals een testdriver) doet daarom eerst een GET om het cookie te krijgen. Het cookie krijgt standaard `Path=/; Secure`, zonder `SameSite`.

- **PIT en Spring-configuratie:** mutanten in `@Configuration`-klassen (bijvoorbeeld een weggehaalde `setJwtGrantedAuthoritiesConverter`) overleven, terwijl een handmatige sabotage op dezelfde plek wél door een test gevangen wordt. Die code draait alleen bij het opbouwen van de gecachete Spring-context. Sluit `*Config` en `*Application` uit (`excludedClasses`), en zet logica die gemeten moet worden in een eigen klasse.
- **PIT met Spring Boot 4 (JUnit 6):** `pitest-maven` 1.30.1 met `pitest-junit5-plugin` 1.2.3 werkt. Zonder die plugin faalt PIT met "Please check you have correctly installed the plugin". Een verkeerd `targetClasses`-filter geeft "No mutations found". Een drempel van 95% bij 40 mutanten laat twee levende mutanten door; kijk dus ook in het rapport.

- **Testcontainers en Docker Desktop:** `Wait.forListeningPort()` is te vroeg tevreden; de proxy van Docker Desktop opent de poort voordat de service draait. De eerste call faalt dan met "HTTP/1.1 header parser received no bytes". Wacht op elk HTTP-antwoord, `Wait.forHttp("/").forStatusCodeMatching(status -> true)`, zodat een ontbrekend endpoint als 404 in het scenario verschijnt en niet als time-out bij het starten.
- **Sabotage op een property die ook uit een omgevingsvariabele komt** (`APP_VERSION` → `app.version`, relaxed binding): de omgevingsvariabele wint van `application.yml`. Een sabotage in de yml doet dan niets. Saboteer in de code die de waarde gebruikt.
- **ArchUnit in een module met alleen testcode** (zoals `acceptance`): laat `ImportOption.DoNotIncludeTests` weg, anders controleert de regel niets.

## Tips

- Draai `mvnw verify` in de commit stage. Bouw het image met Jib (`jib:dockerBuild`) en zet daarbij `-DskipTests`; de tests zijn dan al gedraaid. Gevolg: lokaal kan een image met falende code toch geïnstalleerd worden. Draai daarom altijd eerst de commit stage, en vertrouw niet op een rooktest; die controleert alleen de statuscode.
- Laat de versie in het artefact meekomen, via een omgevingsvariabele uit de deployment. Dan kan een acceptatietest controleren welke build er draait.
