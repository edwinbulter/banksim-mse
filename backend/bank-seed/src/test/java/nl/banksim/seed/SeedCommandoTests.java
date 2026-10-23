package nl.banksim.seed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import nl.banksim.domain.geld.Bedrag;
import nl.banksim.domain.rekening.Iban;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class SeedCommandoTests {

    private final List<String> aanroepen = new ArrayList<>();
    private final KlantAanmaker aanmaker = (alias, saldo) -> {
        aanroepen.add(alias + " " + saldo);
        return new GeseedeKlant("jan-f4de79-1", "geheim-wachtwoord", "NL12SIMB0123456789");
    };
    private final Transacties transacties = (Iban iban, LocalDate datum, Bedrag bedrag, String tegenpartij) ->
            aanroepen.add(iban + " " + datum + " " + bedrag + " " + tegenpartij);
    private final Beheerders beheerders = gebruikersnaam -> {
        aanroepen.add("beheerder " + gebruikersnaam);
        return "beheerder-wachtwoord";
    };
    private final ByteArrayOutputStream uitvoer = new ByteArrayOutputStream();
    private final SeedCommando commando = new SeedCommando(aanmaker, transacties, beheerders, JsonMapper.builder().build(),
            new PrintStream(uitvoer, true, StandardCharsets.UTF_8));

    /** ADR 0015: een login met de realm-rol beheerder, zonder rekeninghouder of rekening. */
    @Test
    void maaktEenBeheerderEnSchrijftGebruikersnaamEnWachtwoordAlsEenJsonRegel() {
        commando.voerUit("beheerder", "--alias", "beheerder-f4de79-1");

        assertThat(aanroepen).containsExactly("beheerder beheerder-f4de79-1");
        assertThat(uitvoer.toString(StandardCharsets.UTF_8)).isEqualTo("""
                {"gebruikersnaam":"beheerder-f4de79-1","wachtwoord":"beheerder-wachtwoord"}
                """);
    }

    @Test
    void maaktEenKlantMetBetaalrekeningEnSchrijftHetResultaatAlsEenJsonRegel() {
        commando.voerUit("klant", "--alias", "jan-f4de79-1", "--betaalrekening", "1842.17");

        assertThat(aanroepen).containsExactly("jan-f4de79-1 1842.17");
        assertThat(uitvoer.toString(StandardCharsets.UTF_8)).isEqualTo(
                "{\"gebruikersnaam\":\"jan-f4de79-1\",\"wachtwoord\":\"geheim-wachtwoord\",\"iban\":\"NL12SIMB0123456789\"}\n");
    }

    @Test
    void boektEenTransactieEnSchrijftHemAlsEenJsonRegel() {
        commando.voerUit("transactie", "--iban", "NL94SIMB0000000001", "--datum", "2026-10-01",
                "--bedrag", "-812.40", "--tegenpartij", "Woningstichting Thuis");

        assertThat(aanroepen).containsExactly("NL94SIMB0000000001 2026-10-01 -812.40 Woningstichting Thuis");
        assertThat(uitvoer.toString(StandardCharsets.UTF_8)).isEqualTo("""
                {"iban":"NL94SIMB0000000001","datum":"2026-10-01","bedrag":"-812.40","tegenpartij":"Woningstichting Thuis"}
                """);
    }

    @Test
    void weigertEenTransactieZonderTegenpartij() {
        assertThatThrownBy(() -> commando.voerUit("transactie", "--iban", "NL94SIMB0000000001", "--datum", "2026-10-01",
                "--bedrag", "-812.40"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("--tegenpartij");
        assertThat(aanroepen).isEmpty();
    }

    @Test
    void boektMeerdereTransactiesInDeGegevenVolgordeEnSchrijftHetAantal() {
        commando.voerUit("transacties", "--iban", "NL94SIMB0000000001",
                "--transactie", "2026-10-01,-812.40,Woningstichting Thuis",
                "--transactie", "2026-10-02,3215.00,Werkgever, B.V.",
                "--transactie", "2026-10-02,-0.01,Tegenpartij 3");

        assertThat(aanroepen).containsExactly(
                "NL94SIMB0000000001 2026-10-01 -812.40 Woningstichting Thuis",
                "NL94SIMB0000000001 2026-10-02 3215.00 Werkgever, B.V.",
                "NL94SIMB0000000001 2026-10-02 -0.01 Tegenpartij 3");
        assertThat(uitvoer.toString(StandardCharsets.UTF_8)).isEqualTo("""
                {"iban":"NL94SIMB0000000001","aantal":3}
                """);
    }

    @Test
    void boektNietsAlsEenVanDeTransactiesOngeldigIs() {
        assertThatThrownBy(() -> commando.voerUit("transacties", "--iban", "NL94SIMB0000000001",
                "--transactie", "2026-10-01,-812.40,Woningstichting Thuis",
                "--transactie", "2026-10-02,3215.00"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("2026-10-02,3215.00");
        assertThat(aanroepen).isEmpty();
    }

    /** Een losse optie aan het eind is een tikfout; die mag niet stilletjes wegvallen (ADR 0018). */
    @Test
    void weigertEenOptieZonderWaardeAanHetEind() {
        assertThatThrownBy(() -> commando.voerUit("transacties", "--iban", "NL94SIMB0000000001",
                "--transactie", "2026-10-01,-812.40,Woningstichting Thuis", "--transactie"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Optie --transactie heeft geen waarde");
        assertThatThrownBy(() -> commando.voerUit("klant", "--alias", "jan", "--betaalrekening"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Optie --betaalrekening heeft geen waarde");
        assertThat(aanroepen).isEmpty();
    }

    @Test
    void weigertEenAanroepZonderCommando() {
        assertThatThrownBy(() -> commando.voerUit())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageStartingWith("Onbekend commando");
        assertThat(aanroepen).isEmpty();
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
