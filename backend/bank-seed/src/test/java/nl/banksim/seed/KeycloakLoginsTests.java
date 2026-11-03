package nl.banksim.seed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withCreatedEntity;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.net.URI;
import java.nio.charset.StandardCharsets;
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
        String basic = Base64.getEncoder().encodeToString("bank-seed:client-geheim".getBytes(StandardCharsets.UTF_8));
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

    /** ADR 0015: de gebruiker krijgt de realm-rol beheerder; het id komt uit de Location van het aanmaken. */
    @Test
    void maaktEenBeheerderEnKentDeRealmRolBeheerderToe() {
        keycloak.expect(requestTo("http://keycloak:8080/realms/banksim-mse/protocol/openid-connect/token"))
                .andRespond(withSuccess("{\"access_token\":\"tok\",\"token_type\":\"Bearer\"}", MediaType.APPLICATION_JSON));
        keycloak.expect(requestTo("http://keycloak:8080/admin/realms/banksim-mse/users"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("""
                        {"username": "beheerder-f4de79-1", "lastName": "Beheerder"}""",
                        org.springframework.test.json.JsonCompareMode.LENIENT))
                .andRespond(withCreatedEntity(URI.create("http://keycloak:8080/admin/realms/banksim-mse/users/u-42")));
        keycloak.expect(requestTo("http://keycloak:8080/admin/realms/banksim-mse/roles/beheerder"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer tok"))
                .andRespond(withSuccess("{\"id\":\"r-7\",\"name\":\"beheerder\",\"composite\":false}",
                        MediaType.APPLICATION_JSON));
        keycloak.expect(requestTo("http://keycloak:8080/admin/realms/banksim-mse/users/u-42/role-mappings/realm"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer tok"))
                .andExpect(content().json("""
                        [{"id": "r-7", "name": "beheerder"}]""", org.springframework.test.json.JsonCompareMode.LENIENT))
                .andRespond(withSuccess());

        String wachtwoord = logins.maakBeheerder("beheerder-f4de79-1");

        assertThat(wachtwoord).isEqualTo("gegenereerd-wachtwoord");
        keycloak.verify();
    }

    /** ADR 0040: met het wachtwoord uit het Secret bank-demo. */
    @Test
    void maaktEenDemoKlantDieNogNietBestaat() {
        verwachtToken();
        verwachtZoeken("jdevries", "[]");
        keycloak.expect(requestTo("http://keycloak:8080/admin/realms/banksim-mse/users"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("""
                        {
                          "username": "jdevries",
                          "enabled": true,
                          "email": "jdevries@banksim-mse.localtest.me",
                          "emailVerified": true,
                          "firstName": "jdevries",
                          "lastName": "Klant",
                          "credentials": [{"type": "password", "value": "klant-ww", "temporary": false}]
                        }""", org.springframework.test.json.JsonCompareMode.STRICT))
                .andRespond(withCreatedEntity(URI.create("http://keycloak:8080/admin/realms/banksim-mse/users/123")));

        logins.zorgVoorKlant("jdevries", "klant-ww");

        keycloak.verify();
    }

    /** ADR 0040: een bestaande login blijft staan; er volgt geen POST, dus ook geen ander wachtwoord. */
    @Test
    void laatEenBestaandeDemoKlantStaan() {
        verwachtToken();
        verwachtZoeken("jdevries", "[{\"id\":\"u-1\",\"username\":\"jdevries\"}]");

        logins.zorgVoorKlant("jdevries", "klant-ww");

        keycloak.verify();
    }

    @Test
    void maaktEenDemoBeheerderDieNogNietBestaatMetDeRealmRol() {
        verwachtToken();
        verwachtZoeken("beheerder", "[]");
        keycloak.expect(requestTo("http://keycloak:8080/admin/realms/banksim-mse/users"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("""
                        {"username": "beheerder", "lastName": "Beheerder",
                         "credentials": [{"type": "password", "value": "beheerder-ww", "temporary": false}]}""",
                        org.springframework.test.json.JsonCompareMode.LENIENT))
                .andRespond(withCreatedEntity(URI.create("http://keycloak:8080/admin/realms/banksim-mse/users/u-42")));
        verwachtRealmRol("u-42");

        logins.zorgVoorBeheerder("beheerder", "beheerder-ww");

        keycloak.verify();
    }

    /** ADR 0040: brak de vorige poging af na het aanmaken, dan krijgt de beheerder hier alsnog zijn rol. */
    @Test
    void geeftEenBestaandeDemoBeheerderAlsnogDeRealmRolZonderHemOpnieuwAanTeMaken() {
        verwachtToken();
        verwachtZoeken("beheerder", "[{\"id\":\"u-42\",\"username\":\"beheerder\"}]");
        verwachtRealmRol("u-42");

        logins.zorgVoorBeheerder("beheerder", "beheerder-ww");

        keycloak.verify();
    }

    private void verwachtToken() {
        keycloak.expect(requestTo("http://keycloak:8080/realms/banksim-mse/protocol/openid-connect/token"))
                .andRespond(withSuccess("{\"access_token\":\"tok\",\"token_type\":\"Bearer\"}", MediaType.APPLICATION_JSON));
    }

    private void verwachtZoeken(String gebruikersnaam, String antwoord) {
        keycloak.expect(requestTo("http://keycloak:8080/admin/realms/banksim-mse/users?username=" + gebruikersnaam + "&exact=true"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer tok"))
                .andRespond(withSuccess(antwoord, MediaType.APPLICATION_JSON));
    }

    private void verwachtRealmRol(String gebruiker) {
        keycloak.expect(requestTo("http://keycloak:8080/admin/realms/banksim-mse/roles/beheerder"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"id\":\"r-7\",\"name\":\"beheerder\",\"composite\":false}",
                        MediaType.APPLICATION_JSON));
        keycloak.expect(requestTo("http://keycloak:8080/admin/realms/banksim-mse/users/" + gebruiker + "/role-mappings/realm"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("""
                        [{"id": "r-7", "name": "beheerder"}]""", org.springframework.test.json.JsonCompareMode.LENIENT))
                .andRespond(withSuccess());
    }
}
