package nl.banksim.seed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import nl.banksim.domain.geld.Bedrag;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class SeedCommandoTests {

    private final List<String> aanroepen = new ArrayList<>();
    private final KlantAanmaker aanmaker = (alias, saldo) -> {
        aanroepen.add(alias + " " + saldo);
        return new GeseedeKlant("jan-f4de79-1", "geheim-wachtwoord", "NL12SIMB0123456789");
    };
    private final ByteArrayOutputStream uitvoer = new ByteArrayOutputStream();
    private final SeedCommando commando = new SeedCommando(aanmaker, JsonMapper.builder().build(),
            new PrintStream(uitvoer, true, StandardCharsets.UTF_8));

    @Test
    void maaktEenKlantMetBetaalrekeningEnSchrijftHetResultaatAlsEenJsonRegel() {
        commando.voerUit("klant", "--alias", "jan-f4de79-1", "--betaalrekening", "1842.17");

        assertThat(aanroepen).containsExactly("jan-f4de79-1 1842.17");
        assertThat(uitvoer.toString(StandardCharsets.UTF_8)).isEqualTo(
                "{\"gebruikersnaam\":\"jan-f4de79-1\",\"wachtwoord\":\"geheim-wachtwoord\",\"iban\":\"NL12SIMB0123456789\"}\n");
    }

    @Test
    void weigertEenOnbekendCommando() {
        assertThatThrownBy(() -> commando.voerUit("rekening", "--alias", "jan"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("rekening");
        assertThat(aanroepen).isEmpty();
    }

    @Test
    void weigertEenKlantZonderAliasOfBetaalrekening() {
        assertThatThrownBy(() -> commando.voerUit("klant", "--betaalrekening", "10.00"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("--alias");
        assertThatThrownBy(() -> commando.voerUit("klant", "--alias", "jan"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("--betaalrekening");
        assertThat(aanroepen).isEmpty();
    }
}
