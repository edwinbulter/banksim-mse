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
- **Mutation testing** met PIT (`pitest-maven`), om te controleren of de tests echt iets vangen.

## Valkuilen (echt opgetreden)

- **Testcontainers met een image op digest:** `new PostgreSQLContainer("postgres:18.6-alpine@sha256:…")` faalt, omdat Testcontainers de naam niet als compatibel herkent. Gebruik `DockerImageName.parse("…@sha256:…").asCompatibleSubstituteFor("postgres")`.
- **Spring Cloud Gateway (server-webmvc) en de HTTP-client van de JDK:** de gateway stuurt de body van elk request gestreamd door, ook bij een GET zonder body. De JDK-client verstuurt dat als lege chunked body (`Transfer-Encoding: chunked`). Daarbij gooide hij in CI af en toe een `NullPointerException` in `Http1Exchange.requestMoreBody` ("bodySubscriber is null"); lokaal trad dat nooit op. Oplossing: een `ClientHttpRequestFactory`-bean met `BufferingClientHttpRequestFactory` om de `JdkClientHttpRequestFactory`. Een lege gebufferde body gaat dan helemaal niet mee. Test met een nep-backend dat een GET geen `Transfer-Encoding`-header heeft.
- **De JDK-client en h2c:** bij `http://` vraagt de client standaard om een upgrade naar HTTP/2 (`Upgrade: h2c`). Zet hem vast op HTTP/1.1 (`HttpClient.Version.HTTP_1_1`) als de backend HTTP/1.1 spreekt. Dit loste de `NullPointerException` hierboven níet op, al leek dat na één groene run wel zo.

- **`jwt()` uit spring-security-test slaat je eigen `JwtAuthenticationConverter` over.** Het geeft standaard alleen `SCOPE_`-authorities; rollen uit een claim (zoals `realm_access.roles`) worden niet omgezet. Een controllertest met `jwt().authorities(...)` bewijst dus niet dat de converter klopt. Test de converter met een echt ondertekend token tegen de `JwtDecoder` uit de configuratie, bijvoorbeeld met een eigen JWKS-endpoint.
- **CSRF voor een SPA (`csrf.spa()`, Spring Security 6.4+):** het token staat in cookie `XSRF-TOKEN` (niet HttpOnly) en moet terug als header `X-XSRF-TOKEN`. Een client zonder cookie krijgt bij een PUT, POST of DELETE een 403. Een API-client (zoals een testdriver) doet daarom eerst een GET om het cookie te krijgen. Het cookie krijgt standaard `Path=/; Secure`, zonder `SameSite`.

- **PIT en Spring-configuratie:** mutanten in `@Configuration`-klassen (bijvoorbeeld een weggehaalde `setJwtGrantedAuthoritiesConverter`) overleven, terwijl een handmatige sabotage op dezelfde plek wél door een test gevangen wordt. Die code draait alleen bij het opbouwen van de gecachete Spring-context. Sluit `*Config` en `*Application` uit (`excludedClasses`), en zet logica die gemeten moet worden in een eigen klasse.
- **PIT met Spring Boot 4 (JUnit 6):** `pitest-maven` 1.30.1 met `pitest-junit5-plugin` 1.2.3 werkt. Zonder die plugin faalt PIT met "Please check you have correctly installed the plugin". Een verkeerd `targetClasses`-filter geeft "No mutations found". Een drempel van 95% bij 40 mutanten laat twee levende mutanten door; kijk dus ook in het rapport.

## Tips

- Draai `mvnw verify` in de commit stage. Bouw het image met Jib (`jib:dockerBuild`) en zet daarbij `-DskipTests`; de tests zijn dan al gedraaid. Gevolg: lokaal kan een image met falende code toch geïnstalleerd worden. Draai daarom altijd eerst de commit stage, en vertrouw niet op een rooktest; die controleert alleen de statuscode.
- Laat de versie in het artefact meekomen, via een omgevingsvariabele uit de deployment. Dan kan een acceptatietest controleren welke build er draait.
