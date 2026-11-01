package nl.banksim.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.parser.OpenAPIV3Parser;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * ADR 0034: elk endpoint van de echte applicatie staat in het contract, en het contract beschrijft geen endpoint dat er
 * niet is. Een nieuw endpoint zonder beschrijving valt hier op, net als in {@code AlleEndpointsTests} voor de rechten.
 */
@SpringBootTest(properties = {
        "banksim.cursor.sleutel=test-sleutel-van-minstens-32-tekens",
        "management.server.port="})
@Import(PostgresTestDatabase.class)
class ContractDektAlleEndpointsTests {

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    RequestMappingHandlerMapping mappings;

    @MockitoBean
    JwtDecoder jwtDecoder;

    @Test
    void elkEndpointStaatInHetContractEnAndersom() {
        Set<String> inDeApplicatie = mappings.getHandlerMethods().entrySet().stream()
                .filter(mapping -> mapping.getValue().getBeanType().getPackageName().startsWith("nl.banksim"))
                .flatMap(mapping -> mapping.getKey().getPathPatternsCondition().getPatternValues().stream()
                        .flatMap(pad -> mapping.getKey().getMethodsCondition().getMethods().stream()
                                .map(methode -> methode.name() + " " + pad)))
                .collect(Collectors.toCollection(TreeSet::new));

        OpenAPI contract = new OpenAPIV3Parser().read(ApiContract.SPECIFICATIE);
        Set<String> inHetContract = contract.getPaths().entrySet().stream()
                .flatMap(pad -> pad.getValue().readOperationsMap().keySet().stream()
                        .map(methode -> methode.name() + " " + pad.getKey()))
                .collect(Collectors.toCollection(TreeSet::new));

        assertThat(inDeApplicatie).as("endpoints van bank-api").isNotEmpty().isEqualTo(inHetContract);
    }
}
