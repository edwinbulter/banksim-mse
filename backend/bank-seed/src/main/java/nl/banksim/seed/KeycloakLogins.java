package nl.banksim.seed;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;

/** Maakt gebruikers aan via de admin-API van Keycloak (ADR 0007), zonder rollen; die komen in slice 6. */
class KeycloakLogins implements Logins {

    private final RestClient keycloak;
    private final SeedProperties.Keycloak instellingen;
    private final Supplier<String> nieuwWachtwoord;

    KeycloakLogins(RestClient.Builder builder, SeedProperties.Keycloak instellingen, Supplier<String> nieuwWachtwoord) {
        this.keycloak = builder.baseUrl(instellingen.internalUrl().toString()).build();
        this.instellingen = instellingen;
        this.nieuwWachtwoord = nieuwWachtwoord;
    }

    @Override
    public String maakLogin(String gebruikersnaam) {
        String wachtwoord = nieuwWachtwoord.get();
        keycloak.post()
                .uri("/admin/realms/{realm}/users", instellingen.realm())
                .headers(headers -> headers.setBearerAuth(token()))
                .contentType(MediaType.APPLICATION_JSON)
                // Met naam en geverifieerd e-mailadres vraagt Keycloak bij het inloggen niet om een profiel aan te vullen.
                .body(Map.of(
                        "username", gebruikersnaam,
                        "enabled", true,
                        "email", gebruikersnaam + "@banksim-mse.localtest.me",
                        "emailVerified", true,
                        "firstName", gebruikersnaam,
                        "lastName", "Testklant",
                        "credentials", List.of(Map.of("type", "password", "value", wachtwoord, "temporary", false))))
                .retrieve()
                .toBodilessEntity();
        return wachtwoord;
    }

    private String token() {
        var formulier = new LinkedMultiValueMap<String, String>();
        formulier.add("grant_type", "client_credentials");
        Token token = keycloak.post()
                .uri("/realms/{realm}/protocol/openid-connect/token", instellingen.realm())
                .headers(headers -> headers.setBasicAuth(instellingen.clientId(), instellingen.clientSecret()))
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(formulier)
                .retrieve()
                .body(Token.class);
        return token.access_token();
    }

    private record Token(String access_token) {
    }
}
