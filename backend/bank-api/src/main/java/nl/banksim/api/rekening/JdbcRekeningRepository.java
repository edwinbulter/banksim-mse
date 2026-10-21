package nl.banksim.api.rekening;

import java.util.List;

import nl.banksim.domain.geld.Bedrag;
import nl.banksim.domain.rekening.Iban;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcRekeningRepository implements RekeningRepository {

    private final JdbcClient jdbc;

    JdbcRekeningRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<Rekening> vanGebruiker(String gebruikersnaam) {
        return jdbc.sql("""
                        SELECT r.soort, r.iban,
                               r.openingssaldo + coalesce((SELECT sum(t.bedrag) FROM transactie t WHERE t.iban = r.iban), 0) AS saldo
                        FROM rekening r JOIN rekeninghouder h ON h.id = r.rekeninghouder_id
                        WHERE h.gebruikersnaam = :gebruikersnaam
                        ORDER BY r.soort, r.iban""")
                .param("gebruikersnaam", gebruikersnaam)
                .query((rij, nummer) -> new Rekening(
                        Soort.van(rij.getString("soort")),
                        Iban.van(rij.getString("iban")),
                        new Bedrag(rij.getBigDecimal("saldo"))))
                .list();
    }
}
