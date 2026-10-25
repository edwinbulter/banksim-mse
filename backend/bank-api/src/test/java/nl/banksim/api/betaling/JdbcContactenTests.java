package nl.banksim.api.betaling;

import static org.assertj.core.api.Assertions.assertThat;

import nl.banksim.domain.grootboek.Contact;
import nl.banksim.domain.rekening.Iban;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/** S7.1: de contactenlijst van de bank, tegen PostgreSQL met de migraties van bank-api. */
@Testcontainers
class JdbcContactenTests {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(DockerImageName
            .parse("postgres:18.6-alpine@sha256:77f585114c32fbca283dc835b0596f4e52b51b4c6662d7810b2f4084f60a1873")
            .asCompatibleSubstituteFor("postgres"));

    static JdbcClient jdbc;

    @BeforeAll
    static void schemaEnContact() {
        var dataSource = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        Flyway.configure().dataSource(dataSource).load().migrate();
        jdbc = JdbcClient.create(dataSource);
        jdbc.sql("INSERT INTO contact (iban, naam) VALUES ('NL67SIMB0000000002', 'Sanne')").update();
        jdbc.sql("INSERT INTO contact (iban, naam) VALUES (:iban, 'Sanne de Vries')").param("iban", Iban.simBank(5L).toString()).update();
        jdbc.sql("INSERT INTO contact (iban, naam) VALUES (:iban, 'Bakkerij 100%')").param("iban", Iban.simBank(4L).toString()).update();
        // Jan is zelf ook een contact; zijn eigen rekening hoort niet bij zijn suggesties.
        jdbc.sql("INSERT INTO contact (iban, naam) VALUES ('NL94SIMB0000000001', 'Jan')").update();
        jdbc.sql("INSERT INTO rekeninghouder (gebruikersnaam, naam) VALUES ('jan', 'Jan')").update();
        jdbc.sql("""
                INSERT INTO rekening (iban, rekeninghouder_id, soort, openingssaldo)
                SELECT 'NL94SIMB0000000001', id, 'betaal', 0.00 FROM rekeninghouder WHERE gebruikersnaam = 'jan'""").update();
        for (long i = 1; i <= 12; i++) {
            jdbc.sql("INSERT INTO contact (iban, naam) VALUES (:iban, :naam)")
                    .param("iban", Iban.simBank(100L + i).toString()).param("naam", "Zus " + i).update();
        }
    }

    private final JdbcContacten contacten = new JdbcContacten(jdbc);

    @Test
    void vindtHetContactMetDeNaamBijEenIbanUitDeContactenlijst() {
        assertThat(contacten.vind(Iban.van("NL67SIMB0000000002")))
                .contains(new Contact(Iban.van("NL67SIMB0000000002"), "Sanne"));
    }

    @Test
    void zoektOpEenDeelVanDeNaamZonderOpHoofdlettersTeLetten() {
        assertThat(contacten.zoek("sanne", "piet")).extracting(Contact::naam).containsExactly("Sanne", "Sanne de Vries");
    }

    @Test
    void zoektOpHetBeginVanHetIbanOokMetSpatiesEnKleineLetters() {
        assertThat(contacten.zoek("nl67 simb 0000", "piet")).extracting(Contact::naam).containsExactly("Sanne");
    }

    @Test
    void eenStukUitHetMiddenVanHetIbanVindtNiets() {
        assertThat(contacten.zoek("SIMB0000000002", "piet")).isEmpty();
    }

    @Test
    void laatDeEigenRekeningenVanDeGebruikerWeg() {
        assertThat(contacten.zoek("Jan", "jan")).isEmpty();
        assertThat(contacten.zoek("Jan", "piet")).extracting(Contact::naam).containsExactly("Jan");
    }

    @Test
    void geeftHoogstensTienSuggesties() {
        assertThat(contacten.zoek("Zus", "piet")).hasSize(10);
    }

    /** Een % of _ in de invoer is gewoon een teken, geen jokerteken van LIKE. */
    @Test
    void eenProcentTekenZoektLetterlijk() {
        assertThat(contacten.zoek("%", "piet")).extracting(Contact::naam).containsExactly("Bakkerij 100%");
    }

    @Test
    void vindtNietsBijEenIbanDatNietInDeContactenlijstStaat() {
        assertThat(contacten.vind(Iban.van("NL40SIMB0000000003"))).isEmpty();
    }
}
