package nl.banksim.seed;

import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;

/** Maakt gebruikers aan via de admin-API van Keycloak (ADR 0007): klanten zonder rol, beheerders met de realm-rol beheerder (ADR 0015). */
class KeycloakLogins implements Logins, Beheerders, DemoLogins {

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
        maakGebruiker(token(), gebruikersnaam, "Testklant", wachtwoord);
        return wachtwoord;
    }

    @Override
    public String maakBeheerder(String gebruikersnaam) {
        String wachtwoord = nieuwWachtwoord.get();
        String token = token();
        kenBeheerderToe(token, idUit(maakGebruiker(token, gebruikersnaam, "Beheerder", wachtwoord)));
        return wachtwoord;
    }

    @Override
    public void zorgVoorKlant(String gebruikersnaam, String wachtwoord) {
        String token = token();
        if (gevonden(token, gebruikersnaam).isEmpty()) {
            maakGebruiker(token, gebruikersnaam, "Klant", wachtwoord);
        }
    }

    @Override
    public void zorgVoorBeheerder(String gebruikersnaam, String wachtwoord) {
        String token = token();
        String id = gevonden(token, gebruikersnaam).orElseGet(() -> idUit(maakGebruiker(token, gebruikersnaam, "Beheerder", wachtwoord)));
        kenBeheerderToe(token, id);
    }

    private Optional<String> gevonden(String token, String gebruikersnaam) {
        Gebruiker[] gebruikers = keycloak.get()
                .uri("/admin/realms/{realm}/users?username={gebruikersnaam}&exact=true", instellingen.realm(), gebruikersnaam)
                .headers(headers -> headers.setBearerAuth(token))
                .retrieve()
                .body(Gebruiker[].class);
        return Arrays.stream(gebruikers).findFirst().map(Gebruiker::id);
    }

    private static String idUit(URI gebruiker) {
        return gebruiker.getPath().substring(gebruiker.getPath().lastIndexOf('/') + 1);
    }

    private void kenBeheerderToe(String token, String id) {
        Rol rol = keycloak.get()
                .uri("/admin/realms/{realm}/roles/{rol}", instellingen.realm(), "beheerder")
                .headers(headers -> headers.setBearerAuth(token))
                .retrieve()
                .body(Rol.class);
        keycloak.post()
                .uri("/admin/realms/{realm}/users/{id}/role-mappings/realm", instellingen.realm(), id)
                .headers(headers -> headers.setBearerAuth(token))
                .contentType(MediaType.APPLICATION_JSON)
                .body(List.of(rol))
                .retrieve()
                .toBodilessEntity();
    }

    /** Maakt de gebruiker aan en geeft de Location terug, met het id van de gebruiker als laatste deel. */
    private URI maakGebruiker(String token, String gebruikersnaam, String achternaam, String wachtwoord) {
        URI locatie = keycloak.post()
                .uri("/admin/realms/{realm}/users", instellingen.realm())
                .headers(headers -> headers.setBearerAuth(token))
                .contentType(MediaType.APPLICATION_JSON)
                // Met naam en geverifieerd e-mailadres vraagt Keycloak bij het inloggen niet om een profiel aan te vullen.
                .body(Map.of(
                        "username", gebruikersnaam,
                        "enabled", true,
                        "email", gebruikersnaam + "@banksim-mse.localtest.me",
                        "emailVerified", true,
                        "firstName", gebruikersnaam,
                        "lastName", achternaam,
                        "credentials", List.of(Map.of("type", "password", "value", wachtwoord, "temporary", false))))
                .retrieve()
                .toBodilessEntity()
                .getHeaders()
                .getLocation();
        if (locatie == null) {
            throw new IllegalStateException("Keycloak gaf geen Location voor gebruiker " + gebruikersnaam);
        }
        return locatie;
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

    /** Zoals Keycloak een gebruiker beschrijft; voor de role-mapping is het id genoeg. */
    private record Gebruiker(String id) {
    }

    /** Zoals Keycloak een realm-rol beschrijft; voor de role-mapping zijn id en naam genoeg. */
    private record Rol(String id, String name) {
    }
}
