package nl.banksim.api.architectuur;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * ADR 0022: het recht van een endpoint staat boven de methode. Ook een open endpoint zegt dat expliciet, met
 * {@code @PreAuthorize("permitAll()")}.
 */
@AnalyzeClasses(packages = "nl.banksim", importOptions = ImportOption.DoNotIncludeTests.class)
class ToegangPerEndpointTests {

    /** {@code @GetMapping}, {@code @PutMapping} enzovoort dragen zelf {@code @RequestMapping}. */
    @ArchTest
    static final ArchRule elkEndpointHeeftPreAuthorize = methods()
            .that().areMetaAnnotatedWith(RequestMapping.class)
            .should().beAnnotatedWith(PreAuthorize.class)
            .because("een nieuw endpoint zonder recht is voor elke ingelogde gebruiker bereikbaar (ADR 0022)");
}
