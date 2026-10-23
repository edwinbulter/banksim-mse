package nl.banksim.api.beveiliging;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.springframework.boot.security.autoconfigure.actuate.web.servlet.EndpointRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * bank-api is resource server (ADR 0008): alleen de BFF roept hem aan, met het access token van de gebruiker.
 * Zonder token alleen de versie, voor de voettekst en de rooktest; {@code /api/beheer/**} alleen met de realm-rol
 * beheerder (ADR 0015).
 */
@Configuration
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

    /** ADR 0015: de realm-rollen van Keycloak ({@code realm_access.roles}) als rechten met {@code ROLE_}. */
    static JwtAuthenticationConverter realmRollen() {
        var converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
            Object rollen = realmAccess == null ? null : realmAccess.get("roles");
            if (!(rollen instanceof Collection<?> lijst)) {
                return List.of();
            }
            return lijst.stream()
                    .map(rol -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + rol))
                    .toList();
        });
        return converter;
    }

    @Bean
    SecurityFilterChain api(HttpSecurity http) throws Exception {
        return http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/versie").permitAll()
                        // ADR 0015: alleen de beheerder, bijvoorbeeld de simulatiedatum (ADR 0013).
                        .requestMatchers("/api/beheer/**").hasRole("beheerder")
                        .anyRequest().authenticated())
                .oauth2ResourceServer(resourceServer -> resourceServer.jwt(jwt -> jwt
                        .jwtAuthenticationConverter(realmRollen())))
                .sessionManagement(sessie -> sessie.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // Geen cookies of browsersessie: de BFF verwijdert ze en stuurt alleen het token.
                .csrf(csrf -> csrf.disable())
                .build();
    }
}
