package nl.banksim.api.architectuur;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * Elke testdatabase komt uit de helper van zijn module, met een grens op de inlog (ADR 0046). Een test die zelf een
 * {@code PostgreSQLContainer} maakt, kan eeuwig wachten als PostgreSQL niet antwoordt; zo bleef PIT hangen. Deze test
 * leest de testbronnen van alle modules van de backend.
 */
class TestdatabaseUitDeHelperTests {

    private static final Pattern NIEUWE_CONTAINER = Pattern.compile("\\bnew\\s+PostgreSQLContainer\\b");

    private static final Set<String> HELPERS = Set.of("PostgresTestDatabase.java", "TestDatabase.java");

    /** Surefire draait in de map van bank-api; de backend is de map erboven. */
    private static final Path BACKEND = Path.of("..");

    @Test
    void alleenDeHelperMaaktEenPostgresContainer() throws IOException {
        List<Path> bronnen = testbronnen();
        List<String> gevonden = new ArrayList<>();
        for (Path bron : bronnen) {
            if (HELPERS.contains(bron.getFileName().toString())) {
                continue;
            }
            List<String> regels = Files.readAllLines(bron);
            for (int i = 0; i < regels.size(); i++) {
                if (NIEUWE_CONTAINER.matcher(regels.get(i)).find()) {
                    gevonden.add(BACKEND.relativize(bron) + ":" + (i + 1) + ": " + regels.get(i).strip());
                }
            }
        }

        assertThat(bronnen).as("testbronnen van de modules van de backend").hasSizeGreaterThan(50);
        assertThat(gevonden).as("een PostgreSQLContainer buiten de helper van de module").isEmpty();
    }

    private static List<Path> testbronnen() throws IOException {
        try (Stream<Path> modules = Files.list(BACKEND)) {
            List<Path> bronnen = new ArrayList<>();
            for (Path test : modules.map(module -> module.resolve("src/test/java")).filter(Files::isDirectory).toList()) {
                try (Stream<Path> bestanden = Files.walk(test)) {
                    bestanden.filter(bestand -> bestand.toString().endsWith(".java")).forEach(bronnen::add);
                }
            }
            return bronnen;
        }
    }
}
