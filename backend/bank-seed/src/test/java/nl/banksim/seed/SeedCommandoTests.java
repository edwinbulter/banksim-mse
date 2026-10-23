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
    private final KlantAanmaker aanmaker = (alias, naam, saldo) -> {
        aanroepen.add(alias + " " + naam + " " + saldo);
        return new GeseedeKlant("jan-f4de79-1", "geheim-wachtwoord", "NL12SIMB0123456789");
    };
    private final Transacties transacties = (Iban iban, Boeking boeking) ->
            aanroepen.add(iban + " " + boeking.tijdstip() + " " + boeking.bedrag() + " " + boeking.tegenpartij() + " "
                    + boeking.type() + " " + boeking.tegenIban().map(Iban::toString).orElse("-"));
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
        commando.voerUit("klant", "--alias", "jan-f4de79-1", "--naam", "Jan", "--betaalrekening", "1842.17");

        assertThat(aanroepen).containsExactly("jan-f4de79-1 Jan 1842.17");
        assertThat(uitvoer.toString(StandardCharsets.UTF_8)).isEqualTo(
                "{\"gebruikersnaam\":\"jan-f4de79-1\",\"wachtwoord\":\"geheim-wachtwoord\",\"iban\":\"NL12SIMB0123456789\"}\n");
    }

    @Test
    void boektEenTransactieEnSchrijftHemAlsEenJsonRegel() {
        commando.voerUit("transactie", "--iban", "NL94SIMB0000000001", "--datum", "2026-10-01",
                "--bedrag", "-812.40", "--tegenpartij", "Woningstichting Thuis");

        assertThat(aanroepen).containsExactly(
                "NL94SIMB0000000001 2026-10-01T00:00 -812.40 Woningstichting Thuis ONLINE_BANKIEREN -");
        assertThat(uitvoer.toString(StandardCharsets.UTF_8)).isEqualTo("""
                {"iban":"NL94SIMB0000000001","datum":"2026-10-01","bedrag":"-812.40","tegenpartij":"Woningstichting Thuis"}
                """);
    }

    /** S4.1: een tijdstip in Europe/Amsterdam, het type en het IBAN van de tegenpartij. */
    @Test
    void boektEenTransactieMetTijdstipTypeEnTegenIban() {
        commando.voerUit("transactie", "--iban", "NL94SIMB0000000001", "--tijdstip", "2026-10-01T06:02",
                "--bedrag", "-812.40", "--tegenpartij", "Woningstichting Thuis", "--type", "INCASSO",
                "--tegen-iban", "NL91ABNA0417164300");

        assertThat(aanroepen).containsExactly(
                "NL94SIMB0000000001 2026-10-01T06:02 -812.40 Woningstichting Thuis INCASSO NL91ABNA0417164300");
        assertThat(uitvoer.toString(StandardCharsets.UTF_8)).isEqualTo("""
                {"iban":"NL94SIMB0000000001","datum":"2026-10-01","bedrag":"-812.40","tegenpartij":"Woningstichting Thuis"}
                """);
    }

    @Test
    void weigertEenTransactieMetDatumEnTijdstipOfZonderAllebei() {
        assertThatThrownBy(() -> commando.voerUit("transactie", "--iban", "NL94SIMB0000000001", "--datum", "2026-10-01",
                "--tijdstip", "2026-10-01T06:02", "--bedrag", "-1.00", "--tegenpartij", "X"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("--datum of --tijdstip");
        assertThatThrownBy(() -> commando.voerUit("transactie", "--iban", "NL94SIMB0000000001",
                "--bedrag", "-1.00", "--tegenpartij", "X"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("--datum of --tijdstip");
        assertThat(aanroepen).isEmpty();
    }

    @Test
    void weigertEenOnbekendType() {
        assertThatThrownBy(() -> commando.voerUit("transactie", "--iban", "NL94SIMB0000000001", "--datum", "2026-10-01",
                "--bedrag", "-1.00", "--tegenpartij", "X", "--type", "CONTANT"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("CONTANT");
        assertThat(aanroepen).isEmpty();
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
                "NL94SIMB0000000001 2026-10-01T00:00 -812.40 Woningstichting Thuis ONLINE_BANKIEREN -",
                "NL94SIMB0000000001 2026-10-02T00:00 3215.00 Werkgever, B.V. ONLINE_BANKIEREN -",
                "NL94SIMB0000000001 2026-10-02T00:00 -0.01 Tegenpartij 3 ONLINE_BANKIEREN -");
        assertThat(uitvoer.toString(StandardCharsets.UTF_8)).isEqualTo("""
                {"iban":"NL94SIMB0000000001","aantal":3}
                """);
    }

    /** S5.1: per boeking ook tijdstip, type en tegen-IBAN, in één pod; een leeg tegen-IBAN is geen IBAN. */
    @Test
    void boektBoekingenMetTijdstipTypeEnTegenIban() {
        commando.voerUit("transacties", "--iban", "NL94SIMB0000000001",
                "--boeking", "2026-10-01T06:02,-812.40,INCASSO,NL91ABNA0417164300,Woningstichting Thuis",
                "--boeking", "2026-10-03T13:26,-63.48,BETAALAUTOMAAT,,Albert Heijn, filiaal 1");

        assertThat(aanroepen).containsExactly(
                "NL94SIMB0000000001 2026-10-01T06:02 -812.40 Woningstichting Thuis INCASSO NL91ABNA0417164300",
                "NL94SIMB0000000001 2026-10-03T13:26 -63.48 Albert Heijn, filiaal 1 BETAALAUTOMAAT -");
        assertThat(uitvoer.toString(StandardCharsets.UTF_8)).isEqualTo("""
                {"iban":"NL94SIMB0000000001","aantal":2}
                """);
    }

    @Test
    void weigertEenOnvolledigeBoeking() {
        assertThatThrownBy(() -> commando.voerUit("transacties", "--iban", "NL94SIMB0000000001",
                "--boeking", "2026-10-01T06:02,-812.40,INCASSO,NL91ABNA0417164300"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tijdstip,bedrag,type,tegenIban,tegenpartij");
        assertThat(aanroepen).isEmpty();
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

    /** Zonder --naam is de naam van de rekeninghouder de alias. */
    @Test
    void maaktEenKlantZonderNaamMetDeAliasAlsNaam() {
        commando.voerUit("klant", "--alias", "jan-f4de79-1", "--betaalrekening", "1842.17");

        assertThat(aanroepen).containsExactly("jan-f4de79-1 jan-f4de79-1 1842.17");
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
