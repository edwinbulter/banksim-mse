package nl.banksim.seed;

import nl.banksim.domain.geld.Bedrag;
import nl.banksim.domain.rekening.Iban;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

/** Schrijft in het schema van bank-api, als databasegebruiker bank_seed (alleen SELECT en INSERT). */
class JdbcRekeninghouders implements Rekeninghouders {

    private final JdbcClient jdbc;

    JdbcRekeninghouders(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional
    public void voegToeMetBetaalrekening(String gebruikersnaam, Iban iban, Bedrag openingssaldo) {
        long id = jdbc.sql("INSERT INTO rekeninghouder (gebruikersnaam) VALUES (:gebruikersnaam) RETURNING id")
                .param("gebruikersnaam", gebruikersnaam)
                .query(Long.class).single();
        jdbc.sql("""
                        INSERT INTO rekening (iban, rekeninghouder_id, soort, openingssaldo)
                        VALUES (:iban, :rekeninghouder, 'betaal', :openingssaldo)""")
                .param("iban", iban.toString())
                .param("rekeninghouder", id)
                .param("openingssaldo", openingssaldo.waarde())
                .update();
    }
}
