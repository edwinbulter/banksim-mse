package nl.banksim.bff;

import org.springframework.boot.security.autoconfigure.actuate.web.servlet.EndpointRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestCustomizers;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;

@Configuration
class BeveiligingConfig {

    static final String LOGIN = "/oauth2/authorization/" + KeycloakClientRegistration.REGISTRATION_ID;

    /** Alleen health, op de aparte management-poort voor de kubelet-probes. */
    @Bean
    @Order(1)
    SecurityFilterChain management(HttpSecurity http) throws Exception {
        return http.securityMatcher(EndpointRequest.toAnyEndpoint())
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .build();
    }

    @Bean
    SecurityFilterChain bff(HttpSecurity http, ClientRegistrationRepository registraties) throws Exception {
        var pkce = new DefaultOAuth2AuthorizationRequestResolver(registraties, "/oauth2/authorization");
        pkce.setAuthorizationRequestCustomizer(OAuth2AuthorizationRequestCustomizers.withPkce());
        return http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/oauth2/**", "/login/**", "/api/versie").permitAll()
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().denyAll())
                .oauth2Login(login -> login
                        .loginPage(LOGIN)
                        .authorizationEndpoint(endpoint -> endpoint.authorizationRequestResolver(pkce)))
                // ADR 0016: het token in het leesbare cookie XSRF-TOKEN; Angular stuurt het terug als X-XSRF-TOKEN.
                .csrf(csrf -> csrf.spa())
                // De Angular-app krijgt een 401 en toont dan de link "Inloggen".
                .exceptionHandling(fouten -> fouten.defaultAuthenticationEntryPointFor(
                        new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
                        PathPatternRequestMatcher.pathPattern("/api/**")))
                .build();
    }
}
