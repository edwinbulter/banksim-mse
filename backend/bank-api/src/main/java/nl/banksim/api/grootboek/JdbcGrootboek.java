package nl.banksim.api.grootboek;

import java.time.OffsetDateTime;
import java.util.Optional;

import nl.banksim.api.simulatie.SimulationClock;
import nl.banksim.domain.grootboek.Boeking;
import nl.banksim.domain.grootboek.Overboeking;
import nl.banksim.domain.rekening.Iban;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Het grootboek in de database van bank-api (ADR 0009, 0024). */
@Repository
class JdbcGrootboek implements Grootboek {

    private final JdbcClient jdbc;

    JdbcGrootboek(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<String> naamBijEigenRekening(String gebruikersnaam, Iban iban) {
        return jdbc.sql("""
                        SELECT h.naam FROM rekening r JOIN rekeninghouder h ON h.id = r.rekeninghouder_id
                        WHERE r.iban = :iban AND h.gebruikersnaam = :gebruikersnaam""")
                .param("iban", iban.toString())
                .param("gebruikersnaam", gebruikersnaam)
                .query(String.class)
                .optional();
    }

    /** Vraagt een lopende databasetransactie: de trigger controleert de twee kanten pas bij commit (ADR 0024). */
    @Override
    public void schrijf(Overboeking overboeking) {
        OffsetDateTime tijdstip = overboeking.tijdstip().atZone(SimulationClock.BANK).toOffsetDateTime();
        long id = jdbc.sql("""
                        INSERT INTO overboeking (type, tijdstip, datum) VALUES (:type, :tijdstip, :datum) RETURNING id""")
                .param("type", overboeking.type().name())
                .param("tijdstip", tijdstip)
                .param("datum", overboeking.datum())
                .query(Long.class)
                .single();
        for (Boeking boeking : overboeking.boekingen()) {
            jdbc.sql("""
                            INSERT INTO transactie (iban, datum, bedrag, tegenpartij, type, tijdstip, tegen_iban, overboeking_id)
                            VALUES (:iban, :datum, :bedrag, :tegenpartij, :type, :tijdstip, :tegenIban, :overboeking)""")
                    .param("iban", boeking.rekening().toString())
                    .param("datum", overboeking.datum())
                    .param("bedrag", boeking.bedrag().waarde())
                    .param("tegenpartij", boeking.tegenpartij().naam())
                    .param("type", overboeking.type().name())
                    .param("tijdstip", tijdstip)
                    .param("tegenIban", boeking.tegenpartij().iban().toString())
                    .param("overboeking", id)
                    .update();
        }
    }
}
