package nl.banksim.bff;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * CFR OWASP Top 10: de terugkeer van Keycloak na het inloggen hoort bij het inlogverzoek van deze sessie. Met een
 * andere {@code state} kan een aanvaller een code van zijn eigen login in Jans browser laten gebruiken (login-CSRF);
 * de BFF weigert die, nog voordat hij de code bij Keycloak inwisselt.
 */
@SpringBootTest(properties = {
        "banksim.bff.public-url=https://bank-mse.localtest.me",
        "banksim.bff.api-url=http://localhost:1",
        "banksim.bff.keycloak.public-url=https://auth-mse.localtest.me",
        "banksim.bff.keycloak.client-secret=geheim"})
@AutoConfigureMockMvc
class InlogTerugkeerTests {

    static final AtomicInteger TOKENAANVRAGEN = new AtomicInteger();
    static final HttpServer KEYCLOAK = nepKeycloak();

    @DynamicPropertySource
    static void keycloak(DynamicPropertyRegistry properties) {
        properties.add("banksim.bff.keycloak.internal-url", () -> "http://localhost:" + KEYCLOAK.getAddress().getPort());
    }

    @Autowired
    MockMvc mvc;

    final MockHttpSession sessie = new MockHttpSession();

    @BeforeEach
    void leeg() {
        TOKENAANVRAGEN.set(0);
    }

    @AfterAll
    static void stop() {
        KEYCLOAK.stop(0);
    }

    @Test
    void eenTerugkeerMetEenAndereStateWordtGeweigerdZonderDeCodeInTeWisselen() throws Exception {
        begin();

        mvc.perform(get("/login/oauth2/code/keycloak").param("code", "code-van-de-aanvaller").param("state", "andere-state")
                        .session(sessie))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(BeveiligingConfig.LOGIN + "?error"));

        assertThat(TOKENAANVRAGEN).as("aanvragen bij het token-endpoint van Keycloak").hasValue(0);
        assertThat(sessie.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY))
                .as("ingelogd in de sessie").isNull();
    }

    /** De controle: met de eigen state wisselt de BFF de code wel in; anders bewijst de 0 hierboven niets. */
    @Test
    void eenTerugkeerMetDeEigenStateWisseltDeCodeInBijKeycloak() throws Exception {
        String state = begin();

        mvc.perform(get("/login/oauth2/code/keycloak").param("code", "code").param("state", state).session(sessie));

        assertThat(TOKENAANVRAGEN).as("aanvragen bij het token-endpoint van Keycloak").hasValue(1);
    }

    /** Begint met inloggen; de BFF onthoudt het inlogverzoek in de sessie en stuurt de state mee naar Keycloak. */
    private String begin() throws Exception {
        String naar = mvc.perform(get(BeveiligingConfig.LOGIN).session(sessie))
                .andExpect(status().is3xxRedirection())
                .andReturn().getResponse().getRedirectedUrl();
        String state = UriComponentsBuilder.fromUriString(naar).build().getQueryParams().getFirst("state");
        return URLDecoder.decode(state, StandardCharsets.UTF_8);
    }

    /** Telt elke aanvraag bij het token-endpoint, en weigert hem: om het inwisselen gaat het hier niet. */
    private static HttpServer nepKeycloak() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            server.createContext("/", uitwisseling -> {
                uitwisseling.getRequestBody().readAllBytes();
                if (uitwisseling.getRequestURI().getPath().endsWith("/token")) TOKENAANVRAGEN.incrementAndGet();
                byte[] body = "{\"error\":\"invalid_grant\"}".getBytes(StandardCharsets.UTF_8);
                uitwisseling.getResponseHeaders().add("Content-Type", "application/json");
                uitwisseling.sendResponseHeaders(400, body.length);
                uitwisseling.getResponseBody().write(body);
                uitwisseling.close();
            });
            server.setExecutor(Executors.newCachedThreadPool());
            server.start();
            return server;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
