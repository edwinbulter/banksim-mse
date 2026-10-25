package nl.banksim.api.betaling;

import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** De Idempotency-Keys in de database van bank-api (V7, ADR 0027). */
@Repository
class JdbcIdempotentie implements Idempotentie {

    private final JdbcClient jdbc;

    JdbcIdempotentie(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * {@code ON CONFLICT DO NOTHING} wacht bij een gelijktijdige poging op de unieke index tot de eerste klaar is; daarna
     * is haar rij zichtbaar (READ COMMITTED), of weg als ze terugrolde.
     */
    @Override
    public Optional<String> claim(String sleutel, String gebruikersnaam, String verzoekHash) {
        int nieuw = jdbc.sql("""
                        INSERT INTO idempotency_sleutel (sleutel, gebruikersnaam, verzoek_hash)
                        VALUES (:sleutel, :gebruikersnaam, :hash) ON CONFLICT DO NOTHING""")
                .param("sleutel", sleutel)
                .param("gebruikersnaam", gebruikersnaam)
                .param("hash", verzoekHash)
                .update();
        if (nieuw == 1) {
            return Optional.empty();
        }
        return Optional.of(jdbc.sql("""
                        SELECT verzoek_hash FROM idempotency_sleutel WHERE sleutel = :sleutel AND gebruikersnaam = :gebruikersnaam""")
                .param("sleutel", sleutel)
                .param("gebruikersnaam", gebruikersnaam)
                .query(String.class)
                .single());
    }
}
