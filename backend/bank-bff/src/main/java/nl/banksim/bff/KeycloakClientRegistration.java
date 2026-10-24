package nl.banksim.bff;

import java.util.Map;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.oidc.IdTokenClaimNames;

/**
 * Expliciet in plaats van met {@code issuer-uri}: geen discovery bij het starten, en de browser gebruikt de publieke
 * host terwijl de BFF zelf via cluster-DNS met Keycloak praat (ADR 0007, 0008).
 */
@Configuration
class KeycloakClientRegistration {

    static final String REGISTRATION_ID = "keycloak";

    @Bean
    ClientRegistrationRepository clientRegistrationRepository(BffProperties properties) {
        BffProperties.Keycloak keycloak = properties.keycloak();
        return new InMemoryClientRegistrationRepository(ClientRegistration.withRegistrationId(REGISTRATION_ID)
                .clientId(keycloak.clientId())
                .clientSecret(keycloak.clientSecret())
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri(properties.publicUrl() + "/login/oauth2/code/{registrationId}")
                .scope("openid", "profile")
                .authorizationUri(keycloak.publiekEndpoint("auth"))
                .tokenUri(keycloak.internEndpoint("token"))
                .jwkSetUri(keycloak.internEndpoint("certs"))
                .issuerUri(keycloak.issuer())
                .userNameAttributeName(IdTokenClaimNames.SUB)
                .providerConfigurationMetadata(Map.of("issuer", keycloak.issuer(),
                        // ADR 0021: hier stuurt de app de browser heen bij het uitloggen.
                        "end_session_endpoint", keycloak.publiekEndpoint("logout")))
                .clientName("Keycloak")
                .build());
    }
}
