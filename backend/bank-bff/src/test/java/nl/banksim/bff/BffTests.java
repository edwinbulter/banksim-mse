package nl.banksim.bff;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import com.sun.net.httpserver.HttpServer;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.util.UriComponentsBuilder;

/** ADR 0008: de browser praat alleen met de BFF; die logt in bij Keycloak en stuurt /api door met het token. */
@SpringBootTest(properties = {
        "banksim.bff.public-url=https://bank-mse.localtest.me",
        "banksim.bff.keycloak.public-url=https://auth-mse.localtest.me",
        "banksim.bff.keycloak.internal-url=http://keycloak:8080",
        "banksim.bff.keycloak.client-secret=geheim"})
@AutoConfigureMockMvc
class BffTests {

    static final HttpServer BANK_API = nepBankApi();
    static final List<String> AUTHORIZATION = new CopyOnWriteArrayList<>();
    static final List<String> COOKIES = new CopyOnWriteArrayList<>();
    static final List<String> UPGRADES = new CopyOnWriteArrayList<>();
    static final List<String> TRANSFER_ENCODINGS = new CopyOnWriteArrayList<>();

    @DynamicPropertySource
    static void bankApi(DynamicPropertyRegistry properties) {
        properties.add("banksim.bff.api-url", () -> "http://localhost:" + BANK_API.getAddress().getPort());
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    ClientRegistrationRepository registraties;

    @BeforeEach
    void leeg() {
        AUTHORIZATION.clear();
        COOKIES.clear();
        UPGRADES.clear();
        TRANSFER_ENCODINGS.clear();
    }

    @AfterAll
    static void stop() {
        BANK_API.stop(0);
    }

    @Test
    void zonderLoginGeeftDeApi401() throws Exception {
        mvc.perform(get("/api/rekeningen")).andExpect(status().isUnauthorized());
        assertThat(AUTHORIZATION).isEmpty();
    }

    @Test
    void inloggenBeginBijDePubliekeKeycloakUrlMetPkce() throws Exception {
        String locatie = mvc.perform(get("/oauth2/authorization/keycloak"))
                .andExpect(status().is3xxRedirection())
                .andReturn().getResponse().getRedirectedUrl();

        var url = UriComponentsBuilder.fromUriString(locatie).build();
        assertThat(url.getScheme() + "://" + url.getHost() + url.getPath())
                .isEqualTo("https://auth-mse.localtest.me/realms/banksim-mse/protocol/openid-connect/auth");
        assertThat(url.getQueryParams().getFirst("client_id")).isEqualTo("bank-bff");
        assertThat(url.getQueryParams().getFirst("code_challenge_method")).isEqualTo("S256");
        assertThat(url.getQueryParams().getFirst("redirect_uri"))
                .isEqualTo("https://bank-mse.localtest.me/login/oauth2/code/keycloak");
    }

    @Test
    void deVersieIsZonderLoginBereikbaar() throws Exception {
        mvc.perform(get("/api/versie"))
                .andExpect(status().isOk())
                .andExpect(content().string("{\"pad\":\"/api/versie\"}"));
        assertThat(AUTHORIZATION).containsExactly("");
    }

    @Test
    void naInloggenStuurtDeBffHetAccessTokenDoorEnGeenCookies() throws Exception {
        var keycloak = registraties.findByRegistrationId("keycloak");
        mvc.perform(get("/api/rekeningen")
                        .cookie(new Cookie("JSESSIONID", "sessie"))
                        // Het test-token van oauth2Login() heeft de waarde "access-token".
                        .with(oauth2Login().clientRegistration(keycloak)))
                .andExpect(status().isOk())
                .andExpect(content().string("{\"pad\":\"/api/rekeningen\"}"));
        assertThat(AUTHORIZATION).containsExactly("Bearer access-token");
        assertThat(COOKIES).containsExactly("");
    }

    /**
     * Zonder vaste versie vraagt de HTTP-client van de JDK bij http:// om een upgrade naar HTTP/2 (h2c). bank-api
     * spreekt HTTP/1.1; de upgradepoging maakte BffTests in CI af en toe rood (run 39489430674).
     */
    @Test
    void stuurtDeApiDoorAlsHttp11ZonderUpgrade() throws Exception {
        mvc.perform(get("/api/versie")).andExpect(status().isOk());
        assertThat(UPGRADES).containsExactly("");
    }

    /**
     * Een GET zonder body gaat zonder body door. De JDK-client verstuurde een lege chunked body en gooide daarbij soms
     * een NullPointerException in Http1Exchange (runs 39489430674 en 39587594544).
     */
    @Test
    void stuurtEenGetZonderBodyDoorZonderBody() throws Exception {
        mvc.perform(get("/api/versie")).andExpect(status().isOk());
        assertThat(TRANSFER_ENCODINGS).containsExactly("");
    }

    /** ADR 0016: Angular leest het CSRF-token uit een cookie en stuurt het terug als header X-XSRF-TOKEN. */
    @Test
    void zetHetCsrfTokenInEenCookieDatJavaScriptKanLezen() throws Exception {
        Cookie token = mvc.perform(get("/api/versie")).andExpect(status().isOk())
                .andReturn().getResponse().getCookie("XSRF-TOKEN");

        assertThat(token).isNotNull();
        assertThat(token.getValue()).isNotBlank();
        assertThat(token.isHttpOnly()).isFalse();
    }

    /** ADR 0016: een verzoek dat iets verandert, zonder token, komt niet bij bank-api. */
    @Test
    void eenPutZonderCsrfTokenGeeft403() throws Exception {
        mvc.perform(put("/api/beheer/simulatiedatum")
                        .contentType("application/json").content("{\"datum\":\"2026-10-05\"}")
                        .with(oauth2Login().clientRegistration(registraties.findByRegistrationId("keycloak"))))
                .andExpect(status().isForbidden());
        assertThat(AUTHORIZATION).isEmpty();
    }

    /** ADR 0016: met het token uit het cookie als header gaat de PUT met het access token door naar bank-api. */
    @Test
    void eenPutMetHetCsrfTokenUitHetCookieGaatDoorNaarBankApi() throws Exception {
        Cookie token = mvc.perform(get("/api/versie")).andReturn().getResponse().getCookie("XSRF-TOKEN");
        AUTHORIZATION.clear();

        mvc.perform(put("/api/beheer/simulatiedatum")
                        .cookie(token)
                        .header("X-XSRF-TOKEN", token.getValue())
                        .contentType("application/json").content("{\"datum\":\"2026-10-05\"}")
                        .with(oauth2Login().clientRegistration(registraties.findByRegistrationId("keycloak"))))
                .andExpect(status().isOk())
                .andExpect(content().string("{\"pad\":\"/api/beheer/simulatiedatum\"}"));
        assertThat(AUTHORIZATION).containsExactly("Bearer access-token");
    }

    /** Geeft het pad terug en onthoudt welke Authorization-, Cookie-, Upgrade- en Transfer-Encoding-header binnenkwamen. */
    private static HttpServer nepBankApi() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            server.createContext("/", uitwisseling -> {
                String authorization = uitwisseling.getRequestHeaders().getFirst("Authorization");
                String cookie = uitwisseling.getRequestHeaders().getFirst("Cookie");
                String upgrade = uitwisseling.getRequestHeaders().getFirst("Upgrade");
                String transferEncoding = uitwisseling.getRequestHeaders().getFirst("Transfer-Encoding");
                // Eerst de (lege, chunked) body lezen, zoals een echte server: antwoorden terwijl de client nog verstuurt,
                // is een race die deze test niet hoort te testen.
                uitwisseling.getRequestBody().readAllBytes();
                AUTHORIZATION.add(authorization == null ? "" : authorization);
                COOKIES.add(cookie == null ? "" : cookie);
                UPGRADES.add(upgrade == null ? "" : upgrade);
                TRANSFER_ENCODINGS.add(transferEncoding == null ? "" : transferEncoding);
                byte[] body = ("{\"pad\":\"" + uitwisseling.getRequestURI().getPath() + "\"}").getBytes(StandardCharsets.UTF_8);
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
}
