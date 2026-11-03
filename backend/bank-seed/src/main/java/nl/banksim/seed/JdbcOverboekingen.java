package nl.banksim.seed;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

import nl.banksim.domain.grootboek.Boeking;
import nl.banksim.domain.grootboek.Overboeking;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionOperations;

/** ADR 0042: schrijft in het schema van bank-api, als databasegebruiker bank_seed (alleen SELECT en INSERT). */
class JdbcOverboekingen implements Overboekingen {

    private static final ZoneId AMSTERDAM = ZoneId.of("Europe/Amsterdam");

    private final JdbcClient jdbc;
    private final TransactionOperations transactie;

    JdbcOverboekingen(JdbcClient jdbc, TransactionOperations transactie) {
        this.jdbc = jdbc;
        this.transactie = transactie;
    }

    /** In één databasetransactie: de trigger controleert elke overboeking bij de commit (ADR 0024). */
    @Override
    public void schrijfAlle(List<Overboeking> overboekingen) {
        transactie.executeWithoutResult(status -> overboekingen.forEach(this::schrijf));
    }

    private void schrijf(Overboeking overboeking) {
        OffsetDateTime tijdstip = overboeking.tijdstip().atZone(AMSTERDAM).toOffsetDateTime();
        long id = jdbc.sql("INSERT INTO overboeking (type, tijdstip, datum) VALUES (:type, :tijdstip, :datum) RETURNING id")
                .param("type", overboeking.type().name())
                .param("tijdstip", tijdstip)
                .param("datum", overboeking.datum())
                .query(Long.class)
                .single();
        for (Boeking boeking : overboeking.boekingen()) {
            jdbc.sql("""
                            INSERT INTO transactie (iban, datum, bedrag, tegenpartij, type, tijdstip, tegen_iban, overboeking_id, omschrijving)
                            VALUES (:iban, :datum, :bedrag, :tegenpartij, :type, :tijdstip, :tegenIban, :overboeking, :omschrijving)""")
                    .param("iban", boeking.rekening().toString())
                    .param("datum", overboeking.datum())
                    .param("bedrag", boeking.bedrag().waarde())
                    .param("tegenpartij", boeking.tegenpartij().naam())
                    .param("type", boeking.type().name())
                    .param("tijdstip", tijdstip)
                    .param("tegenIban", boeking.tegenpartij().iban().toString())
                    .param("overboeking", id)
                    .param("omschrijving", overboeking.omschrijving())
                    .update();
        }
    }
}
