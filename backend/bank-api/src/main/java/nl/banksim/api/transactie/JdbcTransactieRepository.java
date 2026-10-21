package nl.banksim.api.transactie;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import nl.banksim.domain.geld.Bedrag;
import nl.banksim.domain.rekening.Iban;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Keyset-paginering op (datum, id) aflopend, met de index uit V2__transacties.sql (ADR 0011). */
@Repository
class JdbcTransactieRepository implements TransactieRepository {

    private final JdbcClient jdbc;

    JdbcTransactieRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<Pagina> vanRekening(String gebruikersnaam, Iban iban, Optional<Positie> na, int aantal) {
        boolean eigenRekening = jdbc.sql("""
                        SELECT count(*) FROM rekening r JOIN rekeninghouder h ON h.id = r.rekeninghouder_id
                        WHERE r.iban = :iban AND h.gebruikersnaam = :gebruikersnaam""")
                .param("iban", iban.toString())
                .param("gebruikersnaam", gebruikersnaam)
                .query(Long.class).single() > 0;
        if (!eigenRekening) {
            return Optional.empty();
        }
        // Eén rij extra: is die er, dan volgt er nog een pagina.
        List<Rij> rijen = jdbc.sql("""
                        SELECT id, datum, tegenpartij, bedrag FROM transactie
                        WHERE iban = :iban
                          AND (CAST(:naDatum AS date) IS NULL OR (datum, id) < (CAST(:naDatum AS date), :naId))
                        ORDER BY datum DESC, id DESC
                        LIMIT :limiet""")
                .param("iban", iban.toString())
                .param("naDatum", na.map(Positie::datum).orElse(null))
                .param("naId", na.map(Positie::id).orElse(0L))
                .param("limiet", aantal + 1)
                .query((rij, nummer) -> new Rij(rij.getLong("id"), new Transactie(
                        rij.getObject("datum", LocalDate.class),
                        rij.getString("tegenpartij"),
                        new Bedrag(rij.getBigDecimal("bedrag")))))
                .list();
        List<Rij> pagina = rijen.subList(0, Math.min(aantal, rijen.size()));
        Optional<Positie> volgende = rijen.size() > aantal
                ? Optional.of(new Positie(pagina.getLast().transactie().datum(), pagina.getLast().id()))
                : Optional.empty();
        return Optional.of(new Pagina(pagina.stream().map(Rij::transactie).toList(), volgende));
    }

    private record Rij(long id, Transactie transactie) {
    }
}
