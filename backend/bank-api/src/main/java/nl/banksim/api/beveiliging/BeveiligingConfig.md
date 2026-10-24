# BeveiligingConfig: hoe de beveiliging van `bank-api` werkt

Bestand: [`BeveiligingConfig.java`](BeveiligingConfig.java)

## Het idee: een rij filters vóór de controller

Spring Security zet een **filterketen** (`SecurityFilterChain`) tussen het binnenkomende request en je controllers. Elk request gaat eerst door die filters. Pas als die het request doorlaten, komt het bij bijvoorbeeld `RekeningController`.

```
HTTP-request
   │
   ▼
┌─────────────── Spring Security ────────────────┐
│ 1. Welke keten hoort bij dit request?          │
│ 2. Staat er een token in? Is het geldig?       │
│ 3. Mag dit request zonder/met inlog?           │
└────────────────────────────────────────────────┘
   │ doorgelaten                 │ geweigerd
   ▼                             ▼
RekeningController            401 Unauthorized
```

`BeveiligingConfig` definieert met twee `@Bean`-methodes **twee** van zulke ketens.

## Keten 1: `management` (health voor Kubernetes)

```java
@Bean
@Order(1)
SecurityFilterChain management(HttpSecurity http) throws Exception {
    return http.securityMatcher(EndpointRequest.toAnyEndpoint())
            .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
            .build();
}
```

- **`securityMatcher(EndpointRequest.toAnyEndpoint())`**: deze keten geldt **alleen** voor Actuator-endpoints, zoals `/actuator/health`. Die draaien op een aparte poort, 8081 (`management.server.port` in `application.yaml`).
- **`permitAll()`**: daar mag iedereen bij, zonder token. De kubelet (de readiness- en liveness-probes) heeft namelijk geen token.
- **`@Order(1)`**: Spring probeert de ketens op volgorde. Deze keten komt eerst, omdat de tweede keten op *alles* matcht en anders ook de health-requests zou pakken.

## Keten 2: `api` (alles wat over is)

```java
@Bean
SecurityFilterChain api(HttpSecurity http) throws Exception {
    return http
            .authorizeHttpRequests(auth -> auth
                    .requestMatchers("/api/versie").permitAll()
                    .anyRequest().authenticated())
            .oauth2ResourceServer(resourceServer -> resourceServer.jwt(Customizer.withDefaults()))
            .sessionManagement(sessie -> sessie.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .csrf(csrf -> csrf.disable())
            .build();
}
```

Deze keten heeft geen `securityMatcher`, dus hij vangt alle requests die keten 1 niet pakte. Per regel:

### `authorizeHttpRequests(...)`: wie mag wat

| Regel | Betekenis |
| --- | --- |
| `.requestMatchers("/api/versie").permitAll()` | De versie mag zonder inlog: de voettekst toont hem al vóór het inloggen, en de rooktest in `install.sh` roept hem aan. |
| `.anyRequest().authenticated()` | Voor al het andere, zoals `/api/rekeningen`, moet de gebruiker geauthenticeerd zijn. |

De regels worden van boven naar beneden gelezen; de eerste die past, telt.

Deze regels zijn alleen een vangnet. Het eigenlijke recht van een endpoint staat sinds ADR 0022 boven de controller-methode, bijvoorbeeld `@PreAuthorize("hasRole('beheerder')")` op de simulatiedatum. `@EnableMethodSecurity` op `BeveiligingConfig` zet dat aan. Doordat elke slicetest `BeveiligingConfig` importeert, doen de annotaties daar ook mee. `ToegangPerEndpointTests` (ArchUnit) eist de annotatie op elk endpoint, en `AlleEndpointsTests` controleert 401 en 403 voor alle endpoints van de echte applicatie.

### `oauth2ResourceServer(... jwt ...)`: hoe "geauthenticeerd" wordt vastgesteld

Hier zit de kern. `bank-api` is een **resource server** (ADR 0008). Hij logt zelf niemand in, maar accepteert een **access token** dat Keycloak heeft uitgegeven. Deze regel voegt een filter toe (`BearerTokenAuthenticationFilter`) dat bij elk request het volgende doet:

1. **Header lezen.** Het filter zoekt `Authorization: Bearer <token>`. Staat die er niet, dan is het request anoniem.
2. **Token controleren** met een `JwtDecoder`. Spring Boot bouwt die automatisch uit `application.yaml` en de env-variabelen uit de chart:

   | Instelling | Waar vandaan | Wat er gecontroleerd wordt |
   | --- | --- | --- |
   | `jwk-set-uri` | chart: `http://keycloak:8080/realms/banksim-mse/protocol/openid-connect/certs` (intern) | **Handtekening**: haalt de publieke sleutels van Keycloak op en controleert dat het token echt door Keycloak ondertekend is en onderweg niet is aangepast |
   | `issuer-uri` | chart: `https://auth-mse.localtest.me/realms/banksim-mse` (publiek) | **Uitgever**: de claim `iss` moet precies deze realm zijn |
   | `audiences: bank-api` | `application.yaml` | **Doelgroep**: de claim `aud` moet `bank-api` bevatten. Een token voor een andere toepassing wordt geweigerd |
   | (standaard) | – | **Geldigheid**: `exp` en `nbf`, dus niet verlopen |

3. **Gebruiker vastleggen.** Is het token goed, dan zet het filter een `JwtAuthenticationToken` in de **`SecurityContext`**. Daarin zit het hele `Jwt` met alle claims. Precies daar leest `IngelogdeGebruiker` later `preferred_username` uit:

   ```java
   SecurityContextHolder.getContext().getAuthentication().getPrincipal()  // → Jwt
   jwt.getClaimAsString("preferred_username")                             // → "jan-…"
   ```

4. **Weigeren.** Is het token ongeldig, of ontbreekt het bij een endpoint dat `authenticated()` vereist? Dan stuurt Spring Security direct **401** terug met een `WWW-Authenticate: Bearer …`-header. De controller wordt dan nooit aangeroepen.

### `sessionManagement(STATELESS)`

`bank-api` maakt **geen HTTP-sessie** aan en zet geen `JSESSIONID`-cookie. Elk request moet zelf zijn token meesturen. Dat past bij een API: er is geen "ingelogd blijven" op deze server. Dat geheugen zit in de BFF.

### `csrf(disable)`

CSRF-bescherming is bedoeld tegen aanvallen via **cookies** die de browser automatisch meestuurt. `bank-api` gebruikt geen cookies; de BFF haalt ze zelfs weg (`removeRequestHeader(COOKIE)` in `ApiRoute`). Een bearer-token stuurt een browser nooit uit zichzelf mee. CSRF-bescherming voegt hier dus niets toe en zit alleen in de weg.

## Hoe het in het geheel past

```
Browser ──cookie──► BFF (bank-bff) ──Bearer token──► bank-api
          (sessie,                    (tokenRelay)     BeveiligingConfig:
           PKCE-login                                   - handtekening ✓
           bij Keycloak)                                - issuer ✓
                                                        - audience bank-api ✓
                                                        - niet verlopen ✓
                                                        → SecurityContext
                                                        → RekeningController
```

De browser ziet het token nooit. De BFF bewaart het in zijn sessie en zet het bij elk `/api/**`-request als `Bearer`-header richting `bank-api` (ADR 0007/0008).

## En in de tests?

| Test | Wat er met de beveiliging gebeurt |
| --- | --- |
| `RekeningControllerTests` | De **echte** `BeveiligingConfig` via `@Import`. Met `jwt()` worden stap 1 en 2 overgeslagen: er komt meteen een `Jwt` in de `SecurityContext` (stap 3). Daarom is een mock-`JwtDecoder` genoeg. `zonderTokenIs401` test stap 4. |
| `TokenControleTests` | Test juist stap 2 echt: met een nep-Keycloak die publieke sleutels serveert en **echt ondertekende** tokens, ook met een verkeerde audience, issuer of handtekening. |
| `VersieControllerTests` | Controleert dat `/api/versie` zonder token werkt (`permitAll`). |

Zo test elke test één deel: `RekeningControllerTests` controleert of de regels goed zijn toegepast ("wie mag wat"), en `TokenControleTests` controleert of de tokencontrole klopt ("is dit token echt").
