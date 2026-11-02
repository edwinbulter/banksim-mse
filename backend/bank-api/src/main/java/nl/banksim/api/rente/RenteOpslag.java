package nl.banksim.api.rente;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import nl.banksim.api.grootboek.Saldi;
import nl.banksim.domain.geld.Bedrag;
import nl.banksim.domain.rekening.Iban;
import nl.banksim.domain.rente.DagSaldo;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * ADR 0028: wat de maandafsluiting uit de database van bank-api nodig heeft (V10, V11). Zonder interface: er is één
 * implementatie, en de tests draaien tegen PostgreSQL (backlog fase 5.1).
 */
@Repository
class RenteOpslag {

    private final JdbcClient jdbc;
    private final Saldi saldi;

    RenteOpslag(JdbcClient jdbc) {
        this.jdbc = jdbc;
        this.saldi = new Saldi(jdbc);
    }

    /** De spaarrekeningen met een datum van openen. */
    public List<Spaarrekening> spaarrekeningen() {
        return jdbc.sql("""
                        SELECT r.iban, h.naam, r.geopend_op FROM rekening r JOIN rekeninghouder h ON h.id = r.rekeninghouder_id
                        WHERE r.soort = 'spaar' AND r.geopend_op IS NOT NULL ORDER BY r.iban""")
                .query((rij, nummer) -> new Spaarrekening(Iban.van(rij.getString("iban")), rij.getString("naam"),
                        rij.getObject("geopend_op", LocalDate.class)))
                .list();
    }

    /** Deze spaarrekening als hij van deze gebruiker is (ADR 0023); de datum van openen kan ontbreken. */
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

    /** ADR 0038: deze spaarrekening, van wie hij ook is; alleen voor de beheerder. */
    public Optional<Spaarrekening> spaarrekeningInBeheer(Iban iban) {
        return jdbc.sql("""
                        SELECT r.iban, h.naam, r.geopend_op FROM rekening r JOIN rekeninghouder h ON h.id = r.rekeninghouder_id
                        WHERE r.soort = 'spaar' AND r.iban = :iban""")
                .param("iban", iban.toString())
                .query((rij, nummer) -> new Spaarrekening(Iban.van(rij.getString("iban")), rij.getString("naam"),
                        rij.getObject("geopend_op", LocalDate.class)))
                .optional();
    }

    /**
     * Legt vast dat de rente van deze maand wordt bijgeschreven; false als dat al gebeurd is. Wacht bij een gelijktijdige
     * afsluiting op de unieke sleutel; daarna is het al gebeurd.
     */
    public boolean markeer(Iban iban, YearMonth maand) {
        return jdbc.sql("INSERT INTO rentebijschrijving (iban, maand) VALUES (:iban, :maand) ON CONFLICT DO NOTHING")
                .param("iban", iban.toString())
                .param("maand", maand.atDay(1))
                .update() == 1;
    }

    /** ADR 0030: of de rente van deze maand al is bijgeschreven. */
    public boolean isBijgeschreven(Iban iban, YearMonth maand) {
        return jdbc.sql("SELECT count(*) FROM rentebijschrijving WHERE iban = :iban AND maand = :maand")
                .param("iban", iban.toString())
                .param("maand", maand.atDay(1))
                .query(Long.class)
                .single() > 0;
    }

    /** ADR 0030: de rente en de correcties die voor deze maand al op de spaarrekening zijn geboekt. */
    public Bedrag geboekteRente(Iban iban, YearMonth maand) {
        BigDecimal som = jdbc.sql("""
                        SELECT coalesce(sum(bedrag), 0) FROM transactie
                        WHERE iban = :iban AND type = 'RENTE' AND datum BETWEEN :van AND :tm""")
                .param("iban", iban.toString())
                .param("van", maand.atDay(1))
                .param("tm", maand.atEndOfMonth())
                .query(BigDecimal.class)
                .single();
        return new Bedrag(som.setScale(2));
    }

    /**
     * Het eindsaldo van elke dag van `van` tot en met `totEnMet`, zonder de rente die in die periode is geboekt: de rente
     * van een maand telt niet mee in haar eigen berekening (ADR 0030). Rente van eerdere maanden telt wel mee.
     */
    public List<DagSaldo> dagsaldi(Iban iban, LocalDate van, LocalDate totEnMet) {
        // Het saldo bij het begin van de eerste dag.
        BigDecimal saldo = saldi.aanHetEindVan(iban, van.minusDays(1)).waarde();
        Map<LocalDate, BigDecimal> perDag = jdbc.sql("""
                        SELECT datum, sum(bedrag) AS som FROM transactie
                        WHERE iban = :iban AND type <> 'RENTE' AND datum BETWEEN :van AND :tm GROUP BY datum""")
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
