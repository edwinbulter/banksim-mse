package nl.banksim.bff;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.oidc.web.logout.OidcClientInitiatedLogoutSuccessHandler;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;

/**
 * ADR 0021: na het uitloggen bij de BFF een 202 met in {@code Location} de uitlog-URL van Keycloak (RP-initiated
 * logout, met id_token_hint), in plaats van een redirect. De app roept {@code /logout} aan met XHR en navigeert zelf;
 * een redirect naar Keycloak zou daar op CORS stuklopen.
 */
class UitlogUrlAntwoord extends OidcClientInitiatedLogoutSuccessHandler {

    UitlogUrlAntwoord(ClientRegistrationRepository registraties, String terugNaar) {
        super(registraties);
        setPostLogoutRedirectUri(terugNaar);
    }

    @Override
    public void onLogoutSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) {
        response.setStatus(HttpStatus.ACCEPTED.value());
        response.setHeader(HttpHeaders.LOCATION, determineTargetUrl(request, response, authentication));
    }
}
