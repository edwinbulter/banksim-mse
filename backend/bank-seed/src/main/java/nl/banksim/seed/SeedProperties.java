package nl.banksim.seed;

import java.net.URI;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * De instellingen van bank-seed onder {@code banksim.seed}.
 *
 * @param keycloak de admin-API van de realm, via het service account van client bank-seed (ADR 0003)
 * @param demo     de wachtwoorden van de demo-data (ADR 0040); alleen de K8s Job bank-demo krijgt ze
 * @param verkeer  het wachtwoord van de verkeersklanten (ADR 0055); alleen de K8s Job bank-verkeer krijgt het
 */
@ConfigurationProperties("banksim.seed")
public record SeedProperties(Keycloak keycloak, Demo demo, Verkeer verkeer) {

    /**
     * Keycloak, via het service account van bank-seed.
     *
     * @param internalUrl  interne basis-URL van Keycloak via cluster-DNS
     * @param realm        realm-naam
     * @param clientId     client met de rol manage-users
     * @param clientSecret uit het Secret bank-seed
     */
    public record Keycloak(URI internalUrl, String realm, String clientId, String clientSecret) {
    }

    /**
     * De wachtwoorden van de demo-data, uit het Secret bank-demo.
     *
     * @param klantWachtwoord     van alle demo-klanten
     * @param beheerderWachtwoord van de demo-beheerder
     */
    public record Demo(String klantWachtwoord, String beheerderWachtwoord) {
    }

    /**
     * Het wachtwoord van de verkeersklanten, uit het Secret bank-verkeer.
     *
     * @param wachtwoord van alle verkeersklanten
     */
    public record Verkeer(String wachtwoord) {
    }
}
