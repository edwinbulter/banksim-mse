package nl.banksim.seed;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import nl.banksim.domain.rekening.Iban;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/** ADR 0020: één pod per worker leest opdrachten van stdin, één JSON-array per regel, en blijft draaien na een fout. */
class SeedSessieTests {

    private final List<String> aanroepen = new ArrayList<>();
    private final KlantAanmaker aanmaker = (alias, naam, saldo) -> {
        aanroepen.add("klant " + alias);
        return new GeseedeKlant(alias, "geheim-wachtwoord", "NL12SIMB0123456789");
    };
    private final Transacties transacties = (Iban iban, Boeking boeking) -> aanroepen.add("boeking " + iban);
    private final Beheerders beheerders = gebruikersnaam -> {
        aanroepen.add("beheerder " + gebruikersnaam);
        return "beheerder-wachtwoord";
    };
    private final Contacten contacten = (iban, naam) -> aanroepen.add("contact " + iban);
    private final JsonMapper json = JsonMapper.builder().build();
    private final ByteArrayOutputStream uitvoer = new ByteArrayOutputStream();

    private void sessie(String invoer) {
        var uit = new PrintStream(uitvoer, true, StandardCharsets.UTF_8);
        var commando = new SeedCommando(aanmaker, transacties, beheerders, contacten, json, uit);
        new SeedSessie(commando, json, new BufferedReader(new StringReader(invoer)), uit).voerUit();
    }

    @Test
    void voertElkeRegelUitEnSchrijftPerOpdrachtEenRegelMetDePrefix() {
        sessie("""
                ["klant","--alias","jan-1","--betaalrekening","10.00"]
                ["beheerder","--alias","beheerder-2"]
                """);

        assertThat(aanroepen).containsExactly("klant jan-1", "beheerder beheerder-2");
        assertThat(uitvoer.toString(StandardCharsets.UTF_8)).isEqualTo("""
                BANKSIM-SEED {"gebruikersnaam":"jan-1","wachtwoord":"geheim-wachtwoord","iban":"NL12SIMB0123456789"}
                BANKSIM-SEED {"gebruikersnaam":"beheerder-2","wachtwoord":"beheerder-wachtwoord"}
                """);
    }

    @Test
    void eenFouteOpdrachtGeeftEenFoutEnDeVolgendeWerktNog() {
        sessie("""
                ["klant","--alias","jan-1"]
                ["beheerder","--alias","beheerder-2"]
                """);

        assertThat(aanroepen).containsExactly("beheerder beheerder-2");
        assertThat(uitvoer.toString(StandardCharsets.UTF_8)).isEqualTo("""
                BANKSIM-SEED {"fout":"Optie --betaalrekening ontbreekt"}
                BANKSIM-SEED {"gebruikersnaam":"beheerder-2","wachtwoord":"beheerder-wachtwoord"}
                """);
    }

    @Test
    void eenRegelDieGeenJsonArrayIsGeeftEenFout() {
        sessie("""
                klant --alias jan-1
                """);

        assertThat(aanroepen).isEmpty();
        assertThat(uitvoer.toString(StandardCharsets.UTF_8)).startsWith("BANKSIM-SEED {\"fout\":");
    }

    /** Lege regels slaat de sessie over; aan het einde van stdin stopt hij. */
    @Test
    void stoptAanHetEindeVanDeInvoer() {
        sessie("\n");

        assertThat(aanroepen).isEmpty();
        assertThat(uitvoer.toString(StandardCharsets.UTF_8)).isEmpty();
    }
}
