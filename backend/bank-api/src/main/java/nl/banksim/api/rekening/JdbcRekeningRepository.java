package nl.banksim.api.rekening;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import nl.banksim.api.grootboek.Saldi;
import nl.banksim.domain.geld.Bedrag;
import nl.banksim.domain.rekening.Iban;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcRekeningRepository implements RekeningRepository {

    private final JdbcClient jdbc;
    private final Saldi saldi;

    JdbcRekeningRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
        this.saldi = new Saldi(jdbc);
    }

    /** Alleen de rekeningen van een klant: de interne rekening van de bank (V11) hoort niet in het overzicht. */
    @Override
    public List<Rekening> vanGebruiker(String gebruikersnaam, LocalDate vandaag) {
        record SoortEnIban(Soort soort, Iban iban) {
        }
        return jdbc.sql("""
                        SELECT r.soort, r.iban FROM rekening r JOIN rekeninghouder h ON h.id = r.rekeninghouder_id
                        WHERE h.gebruikersnaam = :gebruikersnaam AND r.soort IN ('betaal', 'spaar')
                        ORDER BY r.soort, r.iban""")
                .param("gebruikersnaam", gebruikersnaam)
                .query((rij, nummer) -> new SoortEnIban(Soort.van(rij.getString("soort")), Iban.van(rij.getString("iban"))))
                .list().stream()
                .map(rekening -> new Rekening(rekening.soort(), rekening.iban(), saldi.aanHetEindVan(rekening.iban(), vandaag)))
                .toList();
    }

    @Override
    public Optional<RekeningMetHouder> vanGebruiker(String gebruikersnaam, Iban iban, LocalDate vandaag) {
        return vanKlant(iban)
                .filter(rekening -> rekening.gebruikersnaam().equals(gebruikersnaam))
                .map(rekening -> rekening.metSaldo(iban, saldi.aanHetEindVan(iban, vandaag)));
    }

    @Override
    public Optional<RekeningMetHouder> inBeheer(Iban iban, LocalDate vandaag) {
        return vanKlant(iban).map(rekening -> rekening.metSaldo(iban, saldi.aanHetEindVan(iban, vandaag)));
    }

    /** Alleen een betaal- of spaarrekening: de interne rekening van de bank (V11) is geen rekening van een klant. */
    private Optional<Houder> vanKlant(Iban iban) {
        return jdbc.sql("""
                        SELECT r.soort, h.gebruikersnaam, h.naam FROM rekening r JOIN rekeninghouder h ON h.id = r.rekeninghouder_id
                        WHERE r.iban = :iban AND r.soort IN ('betaal', 'spaar')""")
                .param("iban", iban.toString())
                .query((rij, nummer) -> new Houder(Soort.van(rij.getString("soort")), rij.getString("gebruikersnaam"), rij.getString("naam")))
                .optional();
    }

    /** De soort van de rekening en van wie hij is, nog zonder saldo. */
    private record Houder(Soort soort, String gebruikersnaam, String naam) {

        RekeningMetHouder metSaldo(Iban iban, Bedrag saldo) {
            return new RekeningMetHouder(soort, iban, saldo, naam);
        }
    }
}
