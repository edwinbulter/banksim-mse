package nl.banksim.bff;

import static org.springframework.cloud.gateway.server.mvc.filter.BeforeFilterFunctions.removeRequestHeader;
import static org.springframework.cloud.gateway.server.mvc.filter.BeforeFilterFunctions.uri;
import static org.springframework.cloud.gateway.server.mvc.filter.TokenRelayFilterFunctions.tokenRelay;
import static org.springframework.cloud.gateway.server.mvc.handler.GatewayRouterFunctions.route;
import static org.springframework.cloud.gateway.server.mvc.handler.HandlerFunctions.http;
import static org.springframework.cloud.gateway.server.mvc.predicate.GatewayRequestPredicates.path;

import java.net.http.HttpClient;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;

/** {@code /api/**} naar bank-api, met het access token uit de sessie en zonder de cookies van de browser (ADR 0008). */
@Configuration
class ApiRoute {

    @Bean
    RouterFunction<ServerResponse> bankApi(BffProperties properties) {
        return route("bank-api")
                .route(path("/api/**"), http())
                .before(uri(properties.apiUrl()))
                .before(removeRequestHeader(HttpHeaders.COOKIE))
                .filter(tokenRelay())
                .build();
    }

    /** bank-api spreekt HTTP/1.1; zonder vaste versie vraagt de JDK-client bij elk request om een upgrade naar h2c. */
    @Bean
    ClientHttpRequestFactory bankApiClient() {
        return new JdkClientHttpRequestFactory(HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build());
    }
}
