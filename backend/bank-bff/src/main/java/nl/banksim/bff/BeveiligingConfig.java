package nl.banksim.bff;

import org.springframework.boot.security.autoconfigure.actuate.web.servlet.EndpointRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.HttpSessionOAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestCustomizers;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
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

    /** ADR 0051: de tokens per sessie, niet per gebruiker; uitloggen wist ze met de sessie. */
    @Bean
    OAuth2AuthorizedClientRepository tokensPerSessie() {
        return new HttpSessionOAuth2AuthorizedClientRepository();
    }

    @Bean
    SecurityFilterChain bff(HttpSecurity http, ClientRegistrationRepository registraties, BffProperties properties)
            throws Exception {
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
                // ADR 0021: POST /logout geeft 202 met de uitlog-URL van Keycloak; de app navigeert erheen.
                .logout(logout -> logout.logoutSuccessHandler(
                        new UitlogUrlAntwoord(registraties, properties.publicUrl() + "/")))
                // ADR 0016: het token in het leesbare cookie XSRF-TOKEN; Angular stuurt het terug als X-XSRF-TOKEN.
                // ADR 0050: met SameSite=Lax.
                .csrf(csrf -> csrf.spa().csrfTokenRepository(csrfCookie()))
                // De Angular-app krijgt een 401 en toont dan de link "Inloggen".
                .exceptionHandling(fouten -> fouten.defaultAuthenticationEntryPointFor(
                        new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
                        PathPatternRequestMatcher.pathPattern("/api/**")))
                .build();
    }

    private static CookieCsrfTokenRepository csrfCookie() {
        var repository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        repository.setCookieCustomizer(cookie -> cookie.sameSite("Lax"));
        return repository;
    }
}
