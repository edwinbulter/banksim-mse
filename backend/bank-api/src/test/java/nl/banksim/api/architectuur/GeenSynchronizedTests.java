package nl.banksim.api.architectuur;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * Concurrency met de juiste primitieven (skill atdd §4): geen {@code synchronized}, niet op een methode en niet als
 * blok. Gedeelde toestand staat in PostgreSQL, met vergrendeling in de query (ADR 0026); in Java helpen een
 * concurrente datastructuur, een lock of een atomair type, zo dicht mogelijk bij de toestand. ArchUnit ziet een
 * {@code synchronized}-blok niet, dus deze test leest de bronbestanden van alle modules van de backend.
 */
class GeenSynchronizedTests {

    private static final Pattern SYNCHRONIZED = Pattern.compile("\\bsynchronized\\b");

    /** Surefire draait in de map van bank-api; de backend is de map erboven. */
    private static final Path BACKEND = Path.of("..");

    @Test
    void geenSynchronizedInDeProductiecode() throws IOException {
        List<Path> bronnen = bronnen();
        List<String> gevonden = new ArrayList<>();
        for (Path bron : bronnen) {
            List<String> regels = Files.readAllLines(bron);
            for (int i = 0; i < regels.size(); i++) {
                if (SYNCHRONIZED.matcher(regels.get(i)).find()) {
                    gevonden.add(BACKEND.relativize(bron) + ":" + (i + 1) + ": " + regels.get(i).strip());
                }
            }
        }

        assertThat(bronnen).as("bronbestanden van bank-api, bank-domain, bank-bff en bank-seed").hasSizeGreaterThan(50);
        assertThat(gevonden).as("synchronized in de productiecode").isEmpty();
    }

    private static List<Path> bronnen() throws IOException {
        try (Stream<Path> modules = Files.list(BACKEND)) {
            List<Path> bronnen = new ArrayList<>();
            for (Path main : modules.map(module -> module.resolve("src/main/java")).filter(Files::isDirectory).toList()) {
                try (Stream<Path> bestanden = Files.walk(main)) {
                    bestanden.filter(bestand -> bestand.toString().endsWith(".java")).forEach(bronnen::add);
                }
            }
            return bronnen;
        }
    }
}
