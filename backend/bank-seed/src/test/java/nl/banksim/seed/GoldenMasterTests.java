package nl.banksim.seed;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * S11.1, golden master: de vaste seeds van de generator geven steeds dezelfde demo-data (ADR 0041). Dezelfde saldi en hetzelfde
 * aantal staan in het scenario "De demo-data is bij elke installatie dezelfde". Faalt deze test na een bedoelde wijziging aan de
 * generator, dan horen de nieuwe waarden eerst langs de mens, net als bij een goedgekeurd scenario.
 */
class GoldenMasterTests {

    private static final Map<String, List<String>> SALDI = Map.of(
            "jdevries", List.of("34812.06", "46358.06"),
            "sbakker", List.of("121373.50", "74770.85"),
            "melamrani", List.of("85055.03", "33706.02"),
            "ljansen", List.of("18018.59", "18860.02"),
            "pvisser", List.of("48085.52", "60191.79"),
            "fyilmaz", List.of("148467.93", "69673.39"),
            "dsmit", List.of("131899.28", "53334.43"),
            "edeboer", List.of("70946.42", "54403.24"),
            "rmulder", List.of("22932.56", "37536.13"),
            "nhendriks", List.of("170500.76", "106843.72"));

    @Test
    void deSaldiOpDeBetaalrekeningEnDeSpaarrekening() {
        Map<String, List<String>> saldi = new LinkedHashMap<>();
        for (DemoKlanten.DemoKlant klant : DemoKlanten.ALLE) {
            saldi.put(klant.gebruikersnaam(), List.of(betaalsaldo(klant).toPlainString(), spaarsaldo(klant).toPlainString()));
        }

        assertThat(saldi).isEqualTo(SALDI);
    }

    /** Een inleg, een opname en een betaling tussen klanten hebben twee kanten, en dus twee transacties. */
    @Test
    void deKlantenHebbenSamen17058Transacties() {
        int aantal = Generator.betalingenTussenKlanten(DemoKlanten.ALLE).size() * 2;
        for (DemoKlanten.DemoKlant klant : DemoKlanten.ALLE) {
            aantal += Generator.boekingen(klant).size() + Generator.inleggen(klant).size() * 2 + Generator.opnames(klant).size() * 2
                    + Generator.rente(klant).size();
        }

        assertThat(aantal).isEqualTo(17_058);
    }

    /** Vangt ook een wijziging die de saldi en het aantal gelijk laat, zoals een boeking die naar een andere dag verschuift. */
    @Test
    void elkeBoekingIsDezelfde() throws NoSuchAlgorithmException {
        MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
        for (String regel : regels()) {
            sha256.update((regel + "\n").getBytes(StandardCharsets.UTF_8));
        }

        assertThat(HexFormat.of().formatHex(sha256.digest())).isEqualTo("53e50015654a87971bce64302fa3b8ba22434ec949f957996f09fa42947acf3a");
    }

    private static List<String> regels() {
        List<String> regels = new ArrayList<>();
        for (DemoKlanten.DemoKlant klant : DemoKlanten.ALLE) {
            String wie = klant.gebruikersnaam();
            Generator.boekingen(klant).forEach(boeking -> regels.add(String.join("|", wie, "boeking", boeking.tijdstip().toString(),
                    boeking.bedrag().waarde().toPlainString(), boeking.tegenpartij(), boeking.type().name(), boeking.tegenIban().toString())));
            Generator.inleggen(klant).forEach(inleg -> regels.add(String.join("|", wie, "inleg", inleg.tijdstip().toString(),
                    inleg.bedrag().waarde().toPlainString())));
            Generator.opnames(klant).forEach(opname -> regels.add(String.join("|", wie, "opname", opname.tijdstip().toString(),
                    opname.bedrag().waarde().toPlainString())));
            Generator.rente(klant).forEach(rente -> regels.add(String.join("|", wie, "rente", rente.tijdstip().toString(),
                    rente.bedrag().waarde().toPlainString())));
        }
        Generator.betalingenTussenKlanten(DemoKlanten.ALLE).forEach(betaling -> regels.add(String.join("|", "betaling",
                betaling.tijdstip().toString(), betaling.bedrag().waarde().toPlainString(), betaling.van(), betaling.naar())));
        return regels;
    }

    private static BigDecimal betaalsaldo(DemoKlanten.DemoKlant klant) {
        BigDecimal saldo = klant.beginsaldo().waarde();
        for (Boeking boeking : Generator.boekingen(klant)) {
            saldo = saldo.add(boeking.bedrag().waarde());
        }
        for (Generator.Inleg inleg : Generator.inleggen(klant)) {
            saldo = saldo.subtract(inleg.bedrag().waarde());
        }
        for (Generator.Opname opname : Generator.opnames(klant)) {
            saldo = saldo.add(opname.bedrag().waarde());
        }
        for (Generator.BetalingTussenKlanten betaling : Generator.betalingenTussenKlanten(DemoKlanten.ALLE)) {
            if (betaling.van().equals(klant.gebruikersnaam())) {
                saldo = saldo.subtract(betaling.bedrag().waarde());
            }
            if (betaling.naar().equals(klant.gebruikersnaam())) {
                saldo = saldo.add(betaling.bedrag().waarde());
            }
        }
        return saldo;
    }

    private static BigDecimal spaarsaldo(DemoKlanten.DemoKlant klant) {
        BigDecimal saldo = klant.beginsaldoSpaar().waarde();
        for (Generator.Inleg inleg : Generator.inleggen(klant)) {
            saldo = saldo.add(inleg.bedrag().waarde());
        }
        for (Generator.Opname opname : Generator.opnames(klant)) {
            saldo = saldo.subtract(opname.bedrag().waarde());
        }
        for (Generator.Rente rente : Generator.rente(klant)) {
            saldo = saldo.add(rente.bedrag().waarde());
        }
        return saldo;
    }
}
