package nl.banksim.bff;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

/**
 * ADR 0050: het sessiecookie heeft SameSite=Lax. Dat cookie zet de servletcontainer, niet Spring Security; MockMvc zet
 * het niet, dus deze test draait tegen een echte Tomcat.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "banksim.bff.public-url=https://bank-mse.localtest.me",
        "banksim.bff.api-url=http://localhost:1",
        "banksim.bff.keycloak.public-url=https://auth-mse.localtest.me",
        "banksim.bff.keycloak.internal-url=http://keycloak:8080",
        "banksim.bff.keycloak.client-secret=geheim"})
class SessiecookieTests {

    @LocalServerPort
    int poort;

    /** Het begin van het inloggen bewaart het inlogverzoek in de sessie, en zet dus het sessiecookie. */
    @Test
    void hetSessiecookieHeeftSameSiteLax() throws Exception {
        var verzoek = HttpRequest.newBuilder(URI.create("http://localhost:" + poort + BeveiligingConfig.LOGIN)).build();
        try (var client = HttpClient.newHttpClient()) {
            var antwoord = client.send(verzoek, HttpResponse.BodyHandlers.discarding());

            assertThat(antwoord.headers().allValues("Set-Cookie"))
                    .filteredOn(cookie -> cookie.startsWith("JSESSIONID="))
                    .singleElement().asString().contains("; SameSite=Lax");
        }
    }
}
