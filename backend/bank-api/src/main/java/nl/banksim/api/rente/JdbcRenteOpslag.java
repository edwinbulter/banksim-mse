package nl.banksim.api.rente;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import nl.banksim.domain.geld.Bedrag;
import nl.banksim.domain.rekening.Iban;
import nl.banksim.domain.rente.DagSaldo;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** De gegevens voor de maandafsluiting in de database van bank-api (V10, V11, ADR 0028). */
@Repository
class JdbcRenteOpslag implements RenteOpslag {

    private final JdbcClient jdbc;

    JdbcRenteOpslag(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<Spaarrekening> spaarrekeningen() {
        return jdbc.sql("""
                        SELECT r.iban, h.naam, r.geopend_op FROM rekening r JOIN rekeninghouder h ON h.id = r.rekeninghouder_id
                        WHERE r.soort = 'spaar' AND r.geopend_op IS NOT NULL ORDER BY r.iban""")
                .query((rij, nummer) -> new Spaarrekening(Iban.van(rij.getString("iban")), rij.getString("naam"),
                        rij.getObject("geopend_op", LocalDate.class)))
                .list();
    }

    @Override
    public Optional<Spaarrekening> spaarrekening(String gebruikersnaam, Iban iban) {
        return jdbc.sql("""
                        SELECT r.iban, h.naam, r.geopend_op FROM rekening r JOIN rekeninghouder h ON h.id = r.rekeninghouder_id
                        WHERE r.soort = 'spaar' AND r.iban = :iban AND h.gebruikersnaam = :gebruikersnaam""")
                .param("iban", iban.toString())
                .param("gebruikersnaam", gebruikersnaam)
                .query((rij, nummer) -> new Spaarrekening(Iban.van(rij.getString("iban")), rij.getString("naam"),
                        rij.getObject("geopend_op", LocalDate.class)))
                .optional();
    }

    /** Wacht bij een gelijktijdige afsluiting op de unieke sleutel; daarna is het al gebeurd. */
    @Override
    public boolean markeer(Iban iban, YearMonth maand) {
        return jdbc.sql("INSERT INTO rentebijschrijving (iban, maand) VALUES (:iban, :maand) ON CONFLICT DO NOTHING")
                .param("iban", iban.toString())
                .param("maand", maand.atDay(1))
                .update() == 1;
    }

    @Override
    public List<DagSaldo> dagsaldi(Iban iban, LocalDate van, LocalDate totEnMet) {
        BigDecimal saldo = jdbc.sql("""
                        SELECT r.openingssaldo + coalesce((SELECT sum(t.bedrag) FROM transactie t
                                                           WHERE t.iban = r.iban AND t.datum < :van), 0)
                        FROM rekening r WHERE r.iban = :iban""")
                .param("iban", iban.toString())
                .param("van", van)
                .query(BigDecimal.class)
                .single();
        Map<LocalDate, BigDecimal> perDag = jdbc.sql("""
                        SELECT datum, sum(bedrag) AS som FROM transactie
                        WHERE iban = :iban AND datum BETWEEN :van AND :tm GROUP BY datum""")
                .param("iban", iban.toString())
                .param("van", van)
                .param("tm", totEnMet)
                .query((rij, nummer) -> Map.entry(rij.getObject("datum", LocalDate.class), rij.getBigDecimal("som")))
                .list().stream().collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        List<DagSaldo> dagsaldi = new ArrayList<>();
        for (LocalDate dag = van; !dag.isAfter(totEnMet); dag = dag.plusDays(1)) {
            saldo = saldo.add(perDag.getOrDefault(dag, BigDecimal.ZERO));
            dagsaldi.add(new DagSaldo(dag, new Bedrag(saldo)));
        }
        return dagsaldi;
    }
}
