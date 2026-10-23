package nl.banksim.seed;

import java.time.ZoneId;

import nl.banksim.domain.rekening.Iban;
import org.springframework.jdbc.core.simple.JdbcClient;

/** Schrijft in het schema van bank-api, als databasegebruiker bank_seed (alleen SELECT en INSERT). */
class JdbcTransacties implements Transacties {

    private static final ZoneId AMSTERDAM = ZoneId.of("Europe/Amsterdam");

    private final JdbcClient jdbc;

    JdbcTransacties(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void boek(Iban iban, Boeking boeking) {
        jdbc.sql("""
                        INSERT INTO transactie (iban, datum, bedrag, tegenpartij, type, tijdstip, tegen_iban)
                        VALUES (:iban, :datum, :bedrag, :tegenpartij, :type, :tijdstip, :tegenIban)""")
                .param("iban", iban.toString())
                .param("datum", boeking.datum())
                .param("bedrag", boeking.bedrag().waarde())
                .param("tegenpartij", boeking.tegenpartij())
                .param("type", boeking.type().name())
                // De klok van de bank: een tijdstip vlak na middernacht hoort bij die dag, niet bij de dag ervoor in UTC.
                .param("tijdstip", boeking.tijdstip().atZone(AMSTERDAM).toOffsetDateTime())
                .param("tegenIban", boeking.tegenIban().map(Iban::toString).orElse(null))
                .update();
    }
}
