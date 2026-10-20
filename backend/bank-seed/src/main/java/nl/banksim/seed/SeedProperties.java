package nl.banksim.seed;

import java.net.URI;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** @param keycloak de admin-API van de realm, via het service account van client bank-seed (ADR 0003) */
@ConfigurationProperties("banksim.seed")
public record SeedProperties(Keycloak keycloak) {

    /**
     * @param internalUrl  interne basis-URL van Keycloak via cluster-DNS
     * @param realm        realm-naam
     * @param clientId     client met de rol manage-users
     * @param clientSecret uit het Secret bank-seed
     */
    public record Keycloak(URI internalUrl, String realm, String clientId, String clientSecret) {
    }
}
