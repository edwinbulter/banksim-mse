package nl.banksim.api.transactie;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import nl.banksim.domain.geld.Bedrag;
import nl.banksim.domain.rekening.Iban;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcTransactieRepository implements TransactieRepository {

    private final JdbcClient jdbc;

    JdbcTransactieRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<List<Transactie>> vanRekening(String gebruikersnaam, Iban iban) {
        boolean eigenRekening = jdbc.sql("""
                        SELECT count(*) FROM rekening r JOIN rekeninghouder h ON h.id = r.rekeninghouder_id
                        WHERE r.iban = :iban AND h.gebruikersnaam = :gebruikersnaam""")
                .param("iban", iban.toString())
                .param("gebruikersnaam", gebruikersnaam)
                .query(Long.class).single() > 0;
        if (!eigenRekening) {
            return Optional.empty();
        }
        return Optional.of(jdbc.sql("""
                        SELECT datum, tegenpartij, bedrag FROM transactie
                        WHERE iban = :iban
                        ORDER BY datum DESC, id DESC""")
                .param("iban", iban.toString())
                .query((rij, nummer) -> new Transactie(
                        rij.getObject("datum", LocalDate.class),
                        rij.getString("tegenpartij"),
                        new Bedrag(rij.getBigDecimal("bedrag"))))
                .list());
    }
}
