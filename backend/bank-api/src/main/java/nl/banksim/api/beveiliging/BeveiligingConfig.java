package nl.banksim.api.beveiliging;

import org.springframework.boot.security.autoconfigure.actuate.web.servlet.EndpointRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * bank-api is resource server (ADR 0008): alleen de BFF roept hem aan, met het access token van de gebruiker.
 * Zonder token alleen de versie, voor de voettekst en de rooktest.
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

    @Bean
    SecurityFilterChain api(HttpSecurity http) throws Exception {
        return http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/versie").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(resourceServer -> resourceServer.jwt(Customizer.withDefaults()))
                .sessionManagement(sessie -> sessie.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // Geen cookies of browsersessie: de BFF verwijdert ze en stuurt alleen het token.
                .csrf(csrf -> csrf.disable())
                .build();
    }
}
