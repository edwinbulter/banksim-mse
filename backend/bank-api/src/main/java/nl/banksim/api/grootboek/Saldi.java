package nl.banksim.api.grootboek;

import java.math.BigDecimal;
import java.time.LocalDate;

import nl.banksim.domain.geld.Bedrag;
import nl.banksim.domain.rekening.Iban;
import org.springframework.jdbc.core.simple.JdbcClient;

/** De saldoformule op één plek (backlog fase 3.2): het openingssaldo plus de transacties tot en met de datum. */
public final class Saldi {

    private final JdbcClient jdbc;

    public Saldi(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Bedrag aanHetEindVan(Iban iban, LocalDate datum) {
        return new Bedrag(jdbc.sql("""
                        SELECT r.openingssaldo + coalesce((SELECT sum(t.bedrag) FROM transactie t
                                                           WHERE t.iban = r.iban AND t.datum <= :datum), 0)
                        FROM rekening r WHERE r.iban = :iban""")
                .param("iban", iban.toString())
                .param("datum", datum)
                .query(BigDecimal.class)
                .single());
    }
}
