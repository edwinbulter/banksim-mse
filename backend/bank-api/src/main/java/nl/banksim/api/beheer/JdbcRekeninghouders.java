package nl.banksim.api.beheer;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import nl.banksim.api.grootboek.Saldi;
import nl.banksim.api.sql.Like;
import nl.banksim.domain.rekening.Iban;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * S10.1 in de database van bank-api. Eerst de rekeninghouders en rekeningnummers, dan per rekening het saldo met
 * {@link Saldi}: hoogstens 50 regels, dus hoogstens 100 kleine queries (YAGNI; optimaliseren pas na meten).
 */
@Repository
class JdbcRekeninghouders implements Rekeninghouders {

    private static final int HOOGSTENS = 50;

    private final JdbcClient jdbc;
    private final Saldi saldi;

    JdbcRekeninghouders(JdbcClient jdbc) {
        this.jdbc = jdbc;
        this.saldi = new Saldi(jdbc);
    }

    @Override
    public List<Rekeninghouder> zoek(String tekst, LocalDate vandaag) {
        record Rij(String naam, Iban betaal, Optional<Iban> spaar) {
        }
        String gewoon = Like.letterlijk(tekst.strip());
        return jdbc.sql("""
                        SELECT h.naam, b.iban AS betaal, s.iban AS spaar
                        FROM rekeninghouder h
                        JOIN rekening b ON b.rekeninghouder_id = h.id AND b.soort = 'betaal'
                        LEFT JOIN rekening s ON s.rekeninghouder_id = h.id AND s.soort = 'spaar'
                        WHERE h.naam ILIKE :naam ESCAPE '\\'
                           OR b.iban LIKE :iban ESCAPE '\\'
                           OR s.iban LIKE :iban ESCAPE '\\'
                        ORDER BY h.naam, b.iban, s.iban
                        LIMIT :hoogstens""")
                .param("naam", "%" + gewoon + "%")
                .param("iban", "%" + gewoon.replace(" ", "").toUpperCase(Locale.ROOT) + "%")
                .param("hoogstens", HOOGSTENS)
                .query((rij, nummer) -> new Rij(rij.getString("naam"), Iban.van(rij.getString("betaal")),
                        Optional.ofNullable(rij.getString("spaar")).map(Iban::van)))
                .list().stream()
                .map(rij -> new Rekeninghouder(rij.naam(), rij.betaal(), saldi.aanHetEindVan(rij.betaal(), vandaag),
                        rij.spaar(), rij.spaar().map(spaar -> saldi.aanHetEindVan(spaar, vandaag))))
                .toList();
    }
}
