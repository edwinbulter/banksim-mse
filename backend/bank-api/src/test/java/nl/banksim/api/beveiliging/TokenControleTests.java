package nl.banksim.api.beveiliging;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * ADR 0008: bank-api accepteert alleen tokens van realm banksim-mse die voor bank-api bedoeld zijn. Met de echte
 * JwtDecoder uit de configuratie en een JWKS-endpoint zoals dat van Keycloak.
 */
@WebMvcTest(controllers = TokenControleTests.Beveiligd.class)
@Import({BeveiligingConfig.class, TokenControleTests.Beveiligd.class})
class TokenControleTests {

    static final String ISSUER = "https://auth-mse.localtest.me/realms/banksim-mse";
    static final RSAKey SLEUTEL = sleutel();
    static final HttpServer KEYCLOAK = jwks();

    @DynamicPropertySource
    static void resourceServer(DynamicPropertyRegistry properties) {
        properties.add("spring.security.oauth2.resourceserver.jwt.issuer-uri", () -> ISSUER);
        properties.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri",
                () -> "http://localhost:" + KEYCLOAK.getAddress().getPort() + "/certs");
    }

    @Autowired
    MockMvc mvc;

    @AfterAll
    static void stop() {
        KEYCLOAK.stop(0);
    }

    @Test
    void accepteertEenTokenVanDeRealmVoorBankApi() throws Exception {
        mvc.perform(get("/api/beveiligd").header(HttpHeaders.AUTHORIZATION, bearer(ISSUER, "bank-api")))
                .andExpect(status().isOk());
    }

    @Test
    void weigertEenTokenVoorEenAndereAudience() throws Exception {
        mvc.perform(get("/api/beveiligd").header(HttpHeaders.AUTHORIZATION, bearer(ISSUER, "account")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void weigertEenTokenVanEenAndereRealm() throws Exception {
        mvc.perform(get("/api/beveiligd").header(HttpHeaders.AUTHORIZATION,
                        bearer("https://auth.localtest.me/realms/banksim", "bank-api")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void weigertEenTokenMetEenAndereSleutel() throws Exception {
        mvc.perform(get("/api/beveiligd").header(HttpHeaders.AUTHORIZATION, bearer(ISSUER, "bank-api", sleutel())))
                .andExpect(status().isUnauthorized());
    }

    /** ADR 0015: de realm-rollen van Keycloak staan in realm_access.roles en worden rechten met ROLE_. */
    @Test
    void deRealmRolBeheerderUitHetTokenGeeftToegangTotBeheer() throws Exception {
        mvc.perform(get("/api/beheer/beveiligd")
                        .header(HttpHeaders.AUTHORIZATION, bearer(ISSUER, "bank-api", SLEUTEL, List.of("beheerder"))))
                .andExpect(status().isOk());
    }

    @Test
    void zonderDeRealmRolBeheerderIsBeheer403() throws Exception {
        mvc.perform(get("/api/beheer/beveiligd")
                        .header(HttpHeaders.AUTHORIZATION, bearer(ISSUER, "bank-api", SLEUTEL, List.of("offline_access"))))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/beheer/beveiligd").header(HttpHeaders.AUTHORIZATION, bearer(ISSUER, "bank-api")))
                .andExpect(status().isForbidden());
    }

    private static String bearer(String issuer, String audience) throws JOSEException {
        return bearer(issuer, audience, SLEUTEL);
    }

    private static String bearer(String issuer, String audience, RSAKey sleutel) throws JOSEException {
        return bearer(issuer, audience, sleutel, null);
    }

    private static String bearer(String issuer, String audience, RSAKey sleutel, List<String> realmRollen)
            throws JOSEException {
        var claims = new JWTClaimsSet.Builder()
                .issuer(issuer)
                .audience(audience)
                .subject("jan")
                .claim("preferred_username", "jan")
                .claim("realm_access", realmRollen == null ? null : Map.of("roles", realmRollen))
                .expirationTime(Date.from(Instant.now().plusSeconds(300)))
                .build();
        var jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(SLEUTEL.getKeyID()).build(), claims);
        jwt.sign(new RSASSASigner(sleutel));
        return "Bearer " + jwt.serialize();
    }

    private static RSAKey sleutel() {
        try {
            return new RSAKeyGenerator(2048).keyID("sleutel").generate();
        } catch (JOSEException e) {
            throw new IllegalStateException(e);
        }
    }

    private static HttpServer jwks() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            byte[] body = new JWKSet(SLEUTEL.toPublicJWK()).toString().getBytes(StandardCharsets.UTF_8);
            server.createContext("/certs", uitwisseling -> {
                uitwisseling.getResponseHeaders().add("Content-Type", "application/json");
                uitwisseling.sendResponseHeaders(200, body.length);
                uitwisseling.getResponseBody().write(body);
                uitwisseling.close();
            });
            server.start();
            return server;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Een beveiligd endpoint, los van de rekeningen; alleen om de tokencontrole te testen. Rechten zoals ADR 0022. */
    @org.springframework.web.bind.annotation.RestController
    static class Beveiligd {

        @org.springframework.web.bind.annotation.GetMapping("/api/beveiligd")
        @org.springframework.security.access.prepost.PreAuthorize("isAuthenticated()")
        String beveiligd() {
            return "ok";
        }

        @org.springframework.web.bind.annotation.GetMapping("/api/beheer/beveiligd")
        @org.springframework.security.access.prepost.PreAuthorize("hasRole('beheerder')")
        String beheer() {
            return "ok";
        }
    }
}
