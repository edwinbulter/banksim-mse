package nl.banksim.api.rekening;

import java.time.LocalDate;
import java.util.List;

import nl.banksim.api.grootboek.Saldi;
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
}
