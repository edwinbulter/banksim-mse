package nl.banksim.seed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withCreatedEntity;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.net.URI;
import java.util.Base64;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class KeycloakLoginsTests {

    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer keycloak = MockRestServiceServer.bindTo(builder).build();
    private final KeycloakLogins logins = new KeycloakLogins(builder,
            new SeedProperties.Keycloak(URI.create("http://keycloak:8080"), "banksim-mse", "bank-seed", "client-geheim"),
            () -> "gegenereerd-wachtwoord");

    @Test
    void haaltEenTokenMetHetServiceAccountEnMaaktDeGebruikerMetWachtwoord() {
        String basic = Base64.getEncoder().encodeToString("bank-seed:client-geheim".getBytes());
        keycloak.expect(requestTo("http://keycloak:8080/realms/banksim-mse/protocol/openid-connect/token"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Basic " + basic))
                .andExpect(content().formDataContains(java.util.Map.of("grant_type", "client_credentials")))
                .andRespond(withSuccess("{\"access_token\":\"tok\",\"token_type\":\"Bearer\"}", MediaType.APPLICATION_JSON));
        keycloak.expect(requestTo("http://keycloak:8080/admin/realms/banksim-mse/users"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer tok"))
                .andExpect(content().json("""
                        {
                          "username": "jan-f4de79-1",
                          "enabled": true,
                          "email": "jan-f4de79-1@banksim-mse.localtest.me",
                          "emailVerified": true,
                          "firstName": "jan-f4de79-1",
                          "lastName": "Testklant",
                          "credentials": [{"type": "password", "value": "gegenereerd-wachtwoord", "temporary": false}]
                        }""", org.springframework.test.json.JsonCompareMode.STRICT))
                .andRespond(withCreatedEntity(URI.create("http://keycloak:8080/admin/realms/banksim-mse/users/123")));

        String wachtwoord = logins.maakLogin("jan-f4de79-1");

        assertThat(wachtwoord).isEqualTo("gegenereerd-wachtwoord");
        keycloak.verify();
    }
}
