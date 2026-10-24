package nl.banksim.api.beveiliging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

import java.util.List;

import nl.banksim.api.PostgresTestDatabase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * ADR 0022: elk endpoint van de echte applicatie, zoals Spring ze kent. Een nieuw endpoint doet vanzelf mee. Zonder
 * token is alleen {@code permitAll()} bereikbaar; een klant krijgt bij {@code hasRole('beheerder')} een 403.
 */
@SpringBootTest(properties = {
        "banksim.cursor.sleutel=test-sleutel-van-minstens-32-tekens",
        "management.server.port="})
@AutoConfigureMockMvc
@Import(PostgresTestDatabase.class)
class AlleEndpointsTests {

    static final String PERMIT_ALL = "permitAll()";
    static final String BEHEERDER = "hasRole('beheerder')";

    @Autowired
    MockMvc mvc;

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    RequestMappingHandlerMapping mappings;

    @MockitoBean
    JwtDecoder jwtDecoder;

    @Test
    void zonderTokenIsAlleenPermitAllBereikbaar() throws Exception {
        for (Endpoint endpoint : endpoints()) {
            int status = mvc.perform(endpoint.verzoek()).andReturn().getResponse().getStatus();
            if (PERMIT_ALL.equals(endpoint.recht())) {
                assertThat(status).as(endpoint.toString()).isNotEqualTo(401);
            } else {
                assertThat(status).as(endpoint.toString()).isEqualTo(401);
            }
        }
    }

    @Test
    void eenKlantKrijgtOpElkBeheerEndpoint403() throws Exception {
        List<Endpoint> beheer = endpoints().stream().filter(endpoint -> BEHEERDER.equals(endpoint.recht())).toList();
        assertThat(beheer).as("endpoints met " + BEHEERDER).isNotEmpty();
        for (Endpoint endpoint : beheer) {
            int status = mvc.perform(endpoint.verzoek()
                            .with(jwt().jwt(token -> token.claim("preferred_username", "jan-a1-1"))))
                    .andReturn().getResponse().getStatus();
            assertThat(status).as(endpoint.toString()).isEqualTo(403);
        }
    }

    /** Alleen de eigen controllers; {@code /error} van Spring Boot hoort er niet bij. */
    private List<Endpoint> endpoints() {
        List<Endpoint> endpoints = mappings.getHandlerMethods().entrySet().stream()
                .filter(mapping -> mapping.getValue().getBeanType().getPackageName().startsWith("nl.banksim"))
                .flatMap(mapping -> Endpoint.van(mapping.getKey(), mapping.getValue()).stream())
                .toList();
        assertThat(endpoints).as("endpoints").isNotEmpty();
        return endpoints;
    }

    /** Eén methode en één pad; een padvariabele wordt {@code x}. */
    record Endpoint(HttpMethod methode, String pad, String recht) {

        static List<Endpoint> van(RequestMappingInfo info, HandlerMethod handler) {
            PreAuthorize annotatie = handler.getMethodAnnotation(PreAuthorize.class);
            String recht = annotatie == null ? null : annotatie.value();
            return info.getPathPatternsCondition().getPatternValues().stream()
                    .flatMap(pad -> info.getMethodsCondition().getMethods().stream()
                            .map(methode -> new Endpoint(HttpMethod.valueOf(methode.name()), pad.replaceAll("\\{[^}]+}", "x"), recht)))
                    .toList();
        }

        /** Met een lege JSON-body, zodat een PUT of POST niet al bij het lezen van de body strandt. */
        MockHttpServletRequestBuilder verzoek() {
            return request(methode, pad).contentType(MediaType.APPLICATION_JSON).content("{}");
        }
    }
}
