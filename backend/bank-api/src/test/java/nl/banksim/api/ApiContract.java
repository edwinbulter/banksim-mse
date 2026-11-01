package nl.banksim.api;

import static com.atlassian.oai.validator.mockmvc.OpenApiValidationMatchers.openApi;
import static com.atlassian.oai.validator.whitelist.rule.WhitelistRules.allOf;
import static com.atlassian.oai.validator.whitelist.rule.WhitelistRules.messageHasKey;
import static com.atlassian.oai.validator.whitelist.rule.WhitelistRules.responseStatusTypeIs;

import com.atlassian.oai.validator.OpenApiInteractionValidator;
import com.atlassian.oai.validator.report.LevelResolver;
import com.atlassian.oai.validator.report.ValidationReport;
import com.atlassian.oai.validator.whitelist.StatusType;
import com.atlassian.oai.validator.whitelist.ValidationErrorsWhitelist;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcBuilderCustomizer;
import org.springframework.context.annotation.Bean;

/**
 * ADR 0034: elk antwoord in een controllertest moet passen in het contract {@code api/bank-api.yaml}. Importeer deze
 * configuratie in een test met MockMvc; dan controleert MockMvc het bij elk verzoek, naast de eigen beweringen.
 *
 * <ul>
 *   <li>Alleen de antwoorden. De tests sturen met opzet ook ongeldige verzoeken, zoals een bedrag "25.001"; die
 *       horen een 400 te geven, en zijn dus geen fout van het contract.</li>
 *   <li>Een foutantwoord (4xx) mag zonder body komen: Spring Security en {@code ResponseStatusException} geven in
 *       MockMvc geen body. Komt er wel een body, dan moet die kloppen.</li>
 * </ul>
 */
@TestConfiguration(proxyBeanMethods = false)
public class ApiContract {

    /** Het pad vanuit de module bank-api, waar Maven en IntelliJ de tests draaien. */
    public static final String SPECIFICATIE = "../../api/bank-api.yaml";

    private static final OpenApiInteractionValidator VALIDATOR = OpenApiInteractionValidator
            .createForSpecificationUrl(SPECIFICATIE)
            .withLevelResolver(LevelResolver.create().withLevel("validation.request", ValidationReport.Level.IGNORE).build())
            .withWhitelist(ValidationErrorsWhitelist.create().withRule("Een foutantwoord zonder body",
                    allOf(messageHasKey("validation.response.body.missing"), responseStatusTypeIs(StatusType.CLIENT_ERROR))))
            .build();

    @Bean
    MockMvcBuilderCustomizer volgensHetContract() {
        return builder -> builder.alwaysExpect(openApi().isValid(VALIDATOR));
    }
}
