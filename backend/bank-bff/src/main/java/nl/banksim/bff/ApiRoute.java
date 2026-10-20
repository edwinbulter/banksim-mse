package nl.banksim.bff;

import static org.springframework.cloud.gateway.server.mvc.filter.BeforeFilterFunctions.removeRequestHeader;
import static org.springframework.cloud.gateway.server.mvc.filter.BeforeFilterFunctions.uri;
import static org.springframework.cloud.gateway.server.mvc.filter.TokenRelayFilterFunctions.tokenRelay;
import static org.springframework.cloud.gateway.server.mvc.handler.GatewayRouterFunctions.route;
import static org.springframework.cloud.gateway.server.mvc.handler.HandlerFunctions.http;
import static org.springframework.cloud.gateway.server.mvc.predicate.GatewayRequestPredicates.path;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
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
}
