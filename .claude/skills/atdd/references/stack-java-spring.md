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

## Tips

- Draai `mvnw verify` in de commit stage. Bouw het image met Jib (`jib:dockerBuild`) en zet daarbij `-DskipTests`; de tests zijn dan al gedraaid. Gevolg: lokaal kan een image met falende code toch geïnstalleerd worden. Draai daarom altijd eerst de commit stage, en vertrouw niet op een rooktest; die controleert alleen de statuscode.
- Laat de versie in het artefact meekomen, via een omgevingsvariabele uit de deployment. Dan kan een acceptatietest controleren welke build er draait.
