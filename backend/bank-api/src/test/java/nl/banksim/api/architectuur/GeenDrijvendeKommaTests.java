package nl.banksim.api.architectuur;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import java.util.Set;
import java.util.stream.Stream;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;

/** ADR 0010: bedragen zijn exact; geen double of float in bank-domain en bank-api. */
@AnalyzeClasses(packages = "nl.banksim", importOptions = ImportOption.DoNotIncludeTests.class)
class GeenDrijvendeKommaTests {

    private static final Set<String> VERBODEN = Set.of("double", "float", "java.lang.Double", "java.lang.Float");

    @ArchTest
    static final ArchRule geenVeldenParametersOfReturntypes = classes().should(new ArchCondition<JavaClass>(
            "geen double of float als veld, parameter of returntype hebben") {
        @Override
        public void check(JavaClass klasse, ConditionEvents events) {
            Stream.concat(
                    klasse.getFields().stream().map(veld -> new Gebruik(veld.getFullName(), veld.getRawType())),
                    klasse.getCodeUnits().stream().flatMap(code -> Stream.concat(
                            Stream.of(code.getRawReturnType()), code.getRawParameterTypes().stream())
                            .map(type -> new Gebruik(code.getFullName(), type))))
                    .filter(gebruik -> VERBODEN.contains(gebruik.type().getName()))
                    .forEach(gebruik -> events.add(SimpleConditionEvent.violated(klasse,
                            gebruik.waar() + " gebruikt " + gebruik.type().getName())));
        }
    });

    @ArchTest
    static final ArchRule geenDoubleOfFloatAanroepen = noClasses().should().dependOnClassesThat()
            .belongToAnyOf(Double.class, Float.class);

    private record Gebruik(String waar, JavaClass type) {
    }
}
