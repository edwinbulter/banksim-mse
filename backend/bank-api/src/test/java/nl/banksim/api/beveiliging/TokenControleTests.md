# TokenControleTests: hoe `accepteertEenTokenVanDeRealmVoorBankApi` werkt

De test zet een nagebootste Keycloak op en stuurt één echt ondertekend JWT door de echte beveiligingsketen van
`bank-api`. Het enige dat wordt nagebootst is Keycloak zelf. Hieronder de stappen in de volgorde waarin ze gebeuren.

## 1. Opbouw: een sleutel en een nep-Keycloak (statische velden)

- **`SLEUTEL`**: een RSA-sleutelpaar van 2048 bits met key-id `"sleutel"` (`sleutel()`). De private key ondertekent
  de tokens. De public key gaat naar de nep-Keycloak.
- **`KEYCLOAK`**: een JDK-`HttpServer` op een willekeurige vrije poort (`jwks()`). Op `/certs` geeft hij een JWK Set
  terug met alleen de **public** key. Keycloak doet hetzelfde op `/realms/<realm>/protocol/openid-connect/certs`.

Dit zijn statische velden. Ze bestaan dus al voordat Spring de context opbouwt, en dat moet ook, want de properties in
stap 2 hebben de poort nodig.

## 2. Spring koppelen aan die nep-Keycloak (`@DynamicPropertySource`)

```
issuer-uri  = https://auth-mse.localtest.me/realms/banksim-mse
jwk-set-uri = http://localhost:<poort>/certs
```

Uit `application.yaml` komt daarnaast:

```yaml
spring.security.oauth2.resourceserver.jwt.audiences: bank-api
```

Er staat geen eigen `JwtDecoder` in de code. Spring Boot maakt hem automatisch aan op basis van deze drie properties:

- de handtekening wordt gecontroleerd met de sleutels van `jwk-set-uri`;
- de `iss`-claim moet gelijk zijn aan `issuer-uri`;
- `aud` moet `bank-api` bevatten;
- het token mag niet verlopen zijn (`exp`/`nbf`).

Omdat `jwk-set-uri` is opgegeven, haalt Spring de sleutels direct op en gebruikt het geen OIDC-discovery via de issuer.
De issuer-URL `auth-mse.localtest.me` hoeft dus niet bereikbaar te zijn. Hij dient alleen als verwachte waarde van
`iss`.

## 3. De Spring-context

- `@WebMvcTest(controllers = Beveiligd.class)` start alleen de MVC-laag, met security en de auto-configuratie voor de
  OAuth2 resource server. Er komt geen database of service-laag bij.
- `@Import(BeveiligingConfig.class, ...)` laadt de **productieconfiguratie** van de beveiliging. Volgens de
  `api`-filterchain is alles behalve `/api/versie` `authenticated()`, en het token wordt via
  `oauth2ResourceServer(...jwt(...))` gecontroleerd.
- `Beveiligd` is een kleine testcontroller op `/api/beveiligd` die `"ok"` teruggeeft. Zo staat de test los van de
  rekeningencontrollers. Het gaat hier alleen om de tokencontrole.

## 4. De test zelf

```java
mvc.perform(get("/api/beveiligd").header(AUTHORIZATION, bearer(ISSUER, "bank-api")))
   .andExpect(status().isOk());
```

`bearer(ISSUER, "bank-api")` bouwt een JWT dat lijkt op een access token van Keycloak:

- `iss` = de realm `banksim-mse`;
- `aud` = `bank-api`;
- `sub` en `preferred_username` = `jan`;
- `exp` = over 5 minuten;
- de header heeft `alg=RS256` en `kid="sleutel"`;
- het token is ondertekend met de private key van `SLEUTEL`.

Het resultaat is `"Bearer <jwt>"`.

## 5. Wat er bij het request gebeurt

1. `BearerTokenAuthenticationFilter` haalt het token uit de header.
2. `NimbusJwtDecoder` leest de `kid` en haalt bij de eerste keer de JWKS op bij `http://localhost:<poort>/certs`, dus
   bij de nep-Keycloak. Daarna controleert hij de RS256-handtekening met de public key.
3. De validators controleren `exp`, `iss` (gelijk aan `ISSUER`) en `aud` (bevat `bank-api`). Alle drie kloppen.
4. De request is geauthenticeerd, `Beveiligd.beveiligd()` geeft `"ok"` terug en de test verwacht `200 OK`.

## Waarom dit de "positieve" test is

De drie tests daaronder gebruiken precies dezelfde opbouw en veranderen telkens **één** ding:

| Test | Afwijking | Welke controle faalt |
| --- | --- | --- |
| `weigertEenTokenVoorEenAndereAudience` | `aud=account` | audience-validator |
| `weigertEenTokenVanEenAndereRealm` | `iss` = realm van het origineel | issuer-validator |
| `weigertEenTokenMetEenAndereSleutel` | ondertekend met een nieuwe sleutel, maar met dezelfde `kid` | handtekening |

`accepteertEenTokenVanDeRealmVoorBankApi` bewijst dat een geldig token wél door de keten komt. Zonder deze test zouden de
drie weigertests ook slagen als alles altijd met 401 werd geweigerd, bijvoorbeeld door een verkeerde poort of JWKS.
Samen leggen de vier tests ADR 0008 vast: `bank-api` accepteert alleen tokens van realm `banksim-mse` die voor
`bank-api` bedoeld zijn.

**Klein detail:** in `bearer(...)` staat de `kid` altijd op `SLEUTEL.getKeyID()`, ook als met een andere sleutel wordt
ondertekend. Dat is bewust zo. In de test met de andere sleutel vindt de decoder daardoor wel een sleutel bij de `kid`,
maar faalt de handtekening, en dat is precies wat die test wil aantonen.
