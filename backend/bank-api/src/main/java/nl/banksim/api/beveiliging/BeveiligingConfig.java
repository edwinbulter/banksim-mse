package nl.banksim.api.beveiliging;

import org.springframework.boot.security.autoconfigure.actuate.web.servlet.EndpointRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * bank-api is resource server (ADR 0008): alleen de BFF roept hem aan, met het access token van de gebruiker.
 * Zonder token alleen de versie, voor de voettekst en de rooktest. Het recht per endpoint, zoals de realm-rol
 * beheerder (ADR 0015), staat in {@code @PreAuthorize} boven de controller-methode (ADR 0022).
 */
@Configuration
@EnableMethodSecurity
public class BeveiligingConfig {

    /** Alleen health, op de aparte management-poort voor de kubelet-probes. */
    @Bean
    @Order(1)
    SecurityFilterChain management(HttpSecurity http) throws Exception {
        // EndpointRequest matcht de Actuator-endpoints (/actuator/**), hier op de management-poort 8081.
        return http.securityMatcher(EndpointRequest.toAnyEndpoint())
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .build();
    }

    /** ADR 0015: de realm-rollen van Keycloak als rechten met {@code ROLE_}; de omzetting zelf staat in {@link RealmRollen}. */
    static JwtAuthenticationConverter realmRollen() {
        var converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(new RealmRollen());
        return converter;
    }

    @Bean
    SecurityFilterChain api(HttpSecurity http) throws Exception {
        return http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/versie").permitAll()
                        // Vangnet; het eigenlijke recht staat in @PreAuthorize (ADR 0022).
                        .anyRequest().authenticated())
                .oauth2ResourceServer(resourceServer -> resourceServer.jwt(jwt -> jwt
                        .jwtAuthenticationConverter(realmRollen())))
                .sessionManagement(sessie -> sessie.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // Geen cookies of browsersessie: de BFF verwijdert ze en stuurt alleen het token.
                .csrf(csrf -> csrf.disable())
                .build();
    }
}
