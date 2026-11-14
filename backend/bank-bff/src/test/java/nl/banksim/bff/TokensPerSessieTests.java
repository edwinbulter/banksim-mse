package nl.banksim.bff;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Backlog CFR OWASP Top 10: elke sessie heeft zijn eigen tokens, en uitloggen wist die van de sessie. Jan is ingelogd
 * op zijn laptop en op zijn telefoon: twee sessies van dezelfde gebruiker. De tokens komen in de opslag van de BFF via
 * de bean {@link OAuth2AuthorizedClientRepository}, zoals na een echte login.
 */
@SpringBootTest(properties = {
        "banksim.bff.public-url=https://bank-mse.localtest.me",
        "banksim.bff.keycloak.public-url=https://auth-mse.localtest.me",
        "banksim.bff.keycloak.client-secret=geheim"})
@AutoConfigureMockMvc
class TokensPerSessieTests {

    static final HttpServer KEYCLOAK = nepServer(TokensPerSessieTests::tokenEndpoint);
    static final HttpServer BANK_API = nepServer(TokensPerSessieTests::bankApi);
    static final List<String> AUTHORIZATION = new CopyOnWriteArrayList<>();

    @DynamicPropertySource
    static void nepServers(DynamicPropertyRegistry properties) {
        properties.add("banksim.bff.api-url", () -> "http://localhost:" + BANK_API.getAddress().getPort());
        properties.add("banksim.bff.keycloak.internal-url", () -> "http://localhost:" + KEYCLOAK.getAddress().getPort());
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    ClientRegistrationRepository registraties;

    @Autowired
    OAuth2AuthorizedClientRepository tokens;

    final OAuth2AuthenticationToken jan = ingelogd("jan");
    final MockHttpSession laptop = new MockHttpSession();
    final MockHttpSession telefoon = new MockHttpSession();

    @BeforeEach
    void leeg() {
        AUTHORIZATION.clear();
    }

    @AfterAll
    static void stop() {
        KEYCLOAK.stop(0);
        BANK_API.stop(0);
    }

    @Test
    void eenTweedeLoginVanJanLaatDeTokensVanZijnEersteSessieStaan() {
        bewaar(laptop, "laptop", Duration.ofMinutes(5));
        bewaar(telefoon, "telefoon", Duration.ofMinutes(5));

        assertThat(laad(laptop).getAccessToken().getTokenValue()).isEqualTo("access-laptop");
    }

    @Test
    void naUitloggenZijnDeTokensVanDieSessieWeg() throws Exception {
        bewaar(laptop, "laptop", Duration.ofMinutes(5));
        bewaar(telefoon, "telefoon", Duration.ofMinutes(5));

        logUit(telefoon);

        assertThat(laad(telefoon)).as("tokens van de telefoon").isNull();
        assertThat(laad(laptop).getAccessToken().getTokenValue()).isEqualTo("access-laptop");
    }

    /**
     * Keycloak beëindigt bij het uitloggen de sessie van de telefoon, en weigert daarna het refresh token van de
     * telefoon. Verloopt het access token van de laptop, dan ververst de BFF met het refresh token van de laptop.
     */
    @Test
    void naUitloggenOpDeTelefoonVerverstDeLaptopMetZijnEigenRefreshToken() throws Exception {
        bewaar(laptop, "laptop", Duration.ofMinutes(-5));
        bewaar(telefoon, "telefoon", Duration.ofMinutes(-5));
        logUit(telefoon);

        mvc.perform(get("/api/rekeningen").session(laptop).with(authentication(jan)))
                .andExpect(status().isOk());

        assertThat(AUTHORIZATION).containsExactly("Bearer ververst-laptop");
    }

    /** Zoals na een login op dit apparaat: een access token dat na {@code geldig} verloopt, en een refresh token. */
    private void bewaar(MockHttpSession sessie, String apparaat, Duration geldig) {
        Instant verloopt = Instant.now().plus(geldig);
        var client = new OAuth2AuthorizedClient(registraties.findByRegistrationId("keycloak"), jan.getName(),
                new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, "access-" + apparaat,
                        verloopt.minus(Duration.ofMinutes(5)), verloopt),
                new OAuth2RefreshToken("refresh-" + apparaat, verloopt.minus(Duration.ofMinutes(5))));
        tokens.saveAuthorizedClient(client, jan, verzoek(sessie), new MockHttpServletResponse());
    }

    private OAuth2AuthorizedClient laad(MockHttpSession sessie) {
        return tokens.loadAuthorizedClient("keycloak", jan, verzoek(sessie));
    }

    private void logUit(MockHttpSession sessie) throws Exception {
        Cookie csrf = mvc.perform(get("/api/versie")).andReturn().getResponse().getCookie("XSRF-TOKEN");
        // /api/versie gaat ook naar de nep-bank-api, zonder token; dat verzoek hoort niet bij de test.
        AUTHORIZATION.clear();
        mvc.perform(post("/logout")
                        .session(sessie)
                        .cookie(csrf)
                        .header("X-XSRF-TOKEN", csrf.getValue())
                        .with(authentication(jan)))
                .andExpect(status().isAccepted());
        assertThat(sessie.isInvalid()).as("sessie na uitloggen ongeldig").isTrue();
    }

    private static MockHttpServletRequest verzoek(MockHttpSession sessie) {
        var verzoek = new MockHttpServletRequest();
        verzoek.setSession(sessie);
        return verzoek;
    }

    private static OAuth2AuthenticationToken ingelogd(String sub) {
        var idToken = new OidcIdToken("id-" + sub, Instant.EPOCH, Instant.EPOCH.plusSeconds(300), Map.of("sub", sub));
        return new OAuth2AuthenticationToken(new DefaultOidcUser(Set.of(), idToken), Set.of(), "keycloak");
    }

    /** Het token-endpoint: alleen het refresh token van de laptop is nog geldig; dat van de telefoon is uitgelogd. */
    private static void tokenEndpoint(HttpExchange uitwisseling) throws IOException {
        String formulier = new String(uitwisseling.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        if (formulier.contains("refresh_token=refresh-laptop")) {
            antwoord(uitwisseling, 200,
                    "{\"access_token\":\"ververst-laptop\",\"token_type\":\"Bearer\",\"expires_in\":300}");
        } else {
            antwoord(uitwisseling, 400, "{\"error\":\"invalid_grant\"}");
        }
    }

    private static void bankApi(HttpExchange uitwisseling) throws IOException {
        String authorization = uitwisseling.getRequestHeaders().getFirst("Authorization");
        uitwisseling.getRequestBody().readAllBytes();
        AUTHORIZATION.add(authorization == null ? "" : authorization);
        antwoord(uitwisseling, 200, "[]");
    }

    private static void antwoord(HttpExchange uitwisseling, int status, String json) throws IOException {
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        uitwisseling.getResponseHeaders().add("Content-Type", "application/json");
        uitwisseling.sendResponseHeaders(status, body.length);
        uitwisseling.getResponseBody().write(body);
        uitwisseling.close();
    }

    private static HttpServer nepServer(HttpHandler handler) {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            server.createContext("/", handler);
            server.setExecutor(Executors.newCachedThreadPool());
            server.start();
            return server;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
