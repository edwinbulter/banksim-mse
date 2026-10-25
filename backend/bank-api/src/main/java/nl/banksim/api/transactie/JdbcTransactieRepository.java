package nl.banksim.api.transactie;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import nl.banksim.api.simulatie.SimulationClock;
import nl.banksim.domain.geld.Bedrag;
import nl.banksim.domain.rekening.Iban;
import nl.banksim.domain.transactie.TransactieType;
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
    public Optional<Pagina> vanRekening(String gebruikersnaam, Iban iban, LocalDate vandaag, Zoekfilter filter,
            Optional<Positie> na, int aantal) {
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
                        SELECT t.id, t.datum, t.tegenpartij, t.bedrag, t.type, t.tijdstip, t.tegen_iban, h.naam, t.omschrijving,
                               t.betalingskenmerk
                        FROM transactie t
                        JOIN rekening r ON r.iban = t.iban
                        JOIN rekeninghouder h ON h.id = r.rekeninghouder_id
                        WHERE t.iban = :iban
                          AND t.datum <= :vandaag
                          -- S5.1: elk veld is een vaste voorwaarde met een parameter, of doet niet mee (null).
                          AND (CAST(:tekst AS text) IS NULL
                               OR t.tegenpartij ILIKE CAST(:tekst AS text) ESCAPE '\\'
                               OR t.tegen_iban LIKE CAST(:ibanPatroon AS text) ESCAPE '\\')
                          AND (CAST(:van AS numeric) IS NULL OR abs(t.bedrag) >= CAST(:van AS numeric))
                          AND (CAST(:tm AS numeric) IS NULL OR abs(t.bedrag) <= CAST(:tm AS numeric))
                          AND (CAST(:type AS text) IS NULL OR t.type = CAST(:type AS text))
                          AND (CAST(:richting AS text) IS NULL
                               OR (CAST(:richting AS text) = 'INKOMEND' AND t.bedrag > 0)
                               OR (CAST(:richting AS text) = 'UITGAAND' AND t.bedrag < 0))
                          AND (CAST(:naDatum AS date) IS NULL OR (t.datum, t.id) < (CAST(:naDatum AS date), :naId))
                        ORDER BY t.datum DESC, t.id DESC
                        LIMIT :limiet""")
                .param("iban", iban.toString())
                .param("vandaag", vandaag)
                .param("tekst", filter.tekstPatroon().orElse(null))
                .param("ibanPatroon", filter.ibanPatroon().orElse(null))
                .param("van", filter.van().map(Bedrag::waarde).orElse(null))
                .param("tm", filter.tm().map(Bedrag::waarde).orElse(null))
                .param("type", filter.type().map(Enum::name).orElse(null))
                .param("richting", filter.richting().map(Enum::name).orElse(null))
                .param("naDatum", na.map(Positie::datum).orElse(null))
                .param("naId", na.map(Positie::id).orElse(0L))
                .param("limiet", aantal + 1)
                .query((rij, nummer) -> new Rij(rij.getLong("id"), new Transactie(
                        rij.getObject("datum", LocalDate.class),
                        new Bedrag(rij.getBigDecimal("bedrag")),
                        new Partij(rij.getString("naam"), Optional.of(iban)),
                        new Partij(rij.getString("tegenpartij"),
                                Optional.ofNullable(rij.getString("tegen_iban")).map(Iban::van)),
                        TransactieType.valueOf(rij.getString("type")),
                        // De klok van de bank (ADR 0013), ook al geeft de database het tijdstip in UTC.
                        rij.getObject("tijdstip", OffsetDateTime.class).atZoneSameInstant(SimulationClock.BANK).toOffsetDateTime(),
                        Optional.ofNullable(rij.getString("omschrijving")), Optional.ofNullable(rij.getString("betalingskenmerk")))))
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
