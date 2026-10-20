package nl.banksim.bff;

import java.net.URI;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param publicUrl publieke URL van de bank-app, bijvoorbeeld {@code https://bank-mse.localtest.me}
 * @param apiUrl    interne URL van bank-api
 * @param keycloak  de browser gebruikt de publieke URL van Keycloak, de BFF de interne
 */
@ConfigurationProperties("banksim.bff")
public record BffProperties(URI publicUrl, URI apiUrl, Keycloak keycloak) {

    /**
     * @param publicUrl    publieke basis-URL van Keycloak, ook de basis van de issuer
     * @param internalUrl  interne basis-URL via cluster-DNS
     * @param realm        realm-naam
     * @param clientId     client-id van de BFF
     * @param clientSecret uit het Secret bank-bff
     */
    public record Keycloak(URI publicUrl, URI internalUrl, String realm, String clientId, String clientSecret) {

        public String issuer() {
            return publicUrl + "/realms/" + realm;
        }

        public String publiekEndpoint(String pad) {
            return issuer() + "/protocol/openid-connect/" + pad;
        }

        public String internEndpoint(String pad) {
            return internalUrl + "/realms/" + realm + "/protocol/openid-connect/" + pad;
        }
    }
}
