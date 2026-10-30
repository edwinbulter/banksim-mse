package nl.banksim.api.rente;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import nl.banksim.api.PostgresTestDatabase;
import nl.banksim.api.grootboek.LedgerService;
import nl.banksim.domain.geld.Bedrag;
import nl.banksim.domain.grootboek.Overboeking;
import nl.banksim.domain.grootboek.Partij;
import nl.banksim.domain.rekening.Iban;
import nl.banksim.domain.transactie.TransactieType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.config.CronTask;
import org.springframework.scheduling.config.ScheduledTaskHolder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * ADR 0028, met de echte applicatie en PostgreSQL: de maandafsluiting boekt de rente van elke afgesloten maand als
 * overboeking van de renterekening van BankSim naar de spaarrekening, hooguit één keer per rekening en maand.
 */
@SpringBootTest(properties = {
        "banksim.cursor.sleutel=test-sleutel-van-minstens-32-tekens",
        "management.server.port="})
@Import(PostgresTestDatabase.class)
class RenteServiceTests {

    static final String RENTEREKENING = "NL24SIMB0000000000";

    @Autowired
    RenteService rente;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    LedgerService ledger;

    @MockitoBean
    JwtDecoder jwtDecoder;

    @Autowired
    ScheduledTaskHolder geplandeTaken;

    /** De nachtelijke afsluiting staat ingepland om 00:05 (ADR 0028). */
    @Test
    void deNachtelijkeAfsluitingIsIngeplandOm0005() {
        assertThat(geplandeTaken.getScheduledTasks()).extracting(taak -> taak.getTask())
                .filteredOn(CronTask.class::isInstance).map(CronTask.class::cast)
                .anySatisfy(taak -> {
                    assertThat(taak.getExpression()).isEqualTo("0 5 0 * * *");
                    assertThat(taak.toString()).contains("NachtelijkeAfsluiting.draai");
                });
    }

    /** € 10.000,00 vanaf 1 januari: € 25,48 op 31 januari, van "BankSim", met omschrijving "Rente januari". */
    @Test
    void boektDeRenteVanJanuariOpDeLaatsteDagVanDeMaand() {
        String spaar = spaarrekening("rente-1", 3001L, "10000.00", "2026-01-01");

        rente.sluitAf(LocalDate.of(2026, 2, 1));

        assertThat(mutaties(spaar)).containsExactly("2026-01-31 25.48 RENTE BankSim Rente januari");
        assertThat(jdbc.sql("SELECT bedrag FROM transactie WHERE iban = :iban AND tegen_iban = :spaar")
                .param("iban", RENTEREKENING).param("spaar", spaar).query(BigDecimal.class).single())
                .isEqualByComparingTo("-25.48");
    }

    @Test
    void tweeKeerAfsluitenBoektEenKeer() {
        String spaar = spaarrekening("rente-2", 3002L, "10000.00", "2026-01-01");

        rente.sluitAf(LocalDate.of(2026, 2, 1));
        rente.sluitAf(LocalDate.of(2026, 2, 15));

        assertThat(mutaties(spaar)).hasSize(1);
    }

    /** Een maand is pas afgesloten op haar laatste dag. */
    @Test
    void eenMaandDieNogNietVoorbijIsKrijgtGeenRente() {
        String spaar = spaarrekening("rente-3", 3003L, "10000.00", "2026-01-01");

        rente.sluitAf(LocalDate.of(2026, 1, 30));

        assertThat(mutaties(spaar)).isEmpty();
    }

    /** Geopend op 20 januari: 12 dagen rente, € 9,86. */
    @Test
    void deRenteBegintOpDeDagVanOpenen() {
        String spaar = spaarrekening("rente-4", 3004L, "10000.00", "2026-01-20");

        rente.sluitAf(LocalDate.of(2026, 2, 1));

        assertThat(mutaties(spaar)).containsExactly("2026-01-31 9.86 RENTE BankSim Rente januari");
    }

    /** Een storting op 16 januari telt vanaf die dag: 15 dagen € 10.000,00 en 16 dagen € 20.000,00 = € 38,63. */
    @Test
    void eenStortingHalverwegeDeMaandTeltVanafDieDag() {
        String spaar = spaarrekening("rente-6", 3006L, "10000.00", "2026-01-01");
        jdbc.sql("""
                        INSERT INTO transactie (iban, datum, bedrag, tegenpartij, type, tijdstip)
                        VALUES (:iban, '2026-01-16', 10000.00, 'Jan', 'OVERSCHRIJVING', '2026-01-16T10:00+01:00')""")
                .param("iban", spaar).update();

        rente.sluitAf(LocalDate.of(2026, 2, 1));

        assertThat(mutaties(spaar)).contains("2026-01-31 38.63 RENTE BankSim Rente januari");
    }

    /** Elke afgesloten maand sinds het openen; februari telt de rente van januari mee in het eindsaldo. */
    @Test
    void sluitAlleMaandenAfSindsHetOpenen() {
        String spaar = spaarrekening("rente-5", 3005L, "10000.00", "2026-01-01");

        rente.sluitAf(LocalDate.of(2026, 3, 31));

        assertThat(mutaties(spaar)).extracting(mutatie -> mutatie.substring(0, 10))
                .containsExactly("2026-01-31", "2026-02-28", "2026-03-31");
    }

    /**
     * S8.1 (FO): de lopende rente telt van de 1e van de maand tot en met vandaag. Na de rente van januari (€ 25,48):
     * € 10.025,48 × 0,03 / 365 × 15 dagen = € 12,36.
     */
    @Test
    void deLopendeRenteTeltVanDeEersteVanDeMaandTotEnMetVandaag() {
        String spaar = spaarrekening("rente-7", 3007L, "10000.00", "2026-01-01");
        rente.sluitAf(LocalDate.of(2026, 2, 15));

        assertThat(rente.van("rente-7", Iban.van(spaar), LocalDate.of(2026, 2, 15))).contains(Bedrag.van("12.36"));
    }

    /** Geopend op 10 februari: 6 dagen € 10.000,00 = € 4,93. */
    @Test
    void deLopendeRenteBegintOpDeDagVanOpenen() {
        String spaar = spaarrekening("rente-8", 3008L, "10000.00", "2026-02-10");

        assertThat(rente.van("rente-8", Iban.van(spaar), LocalDate.of(2026, 2, 15))).contains(Bedrag.van("4.93"));
    }

    /** De simulatiedatum ligt voor de dag van openen: nog geen rente. */
    @Test
    void voorDeDagVanOpenenIsDeLopendeRenteNul() {
        String spaar = spaarrekening("rente-9", 3009L, "10000.00", "2026-02-10");

        assertThat(rente.van("rente-9", Iban.van(spaar), LocalDate.of(2026, 2, 9))).contains(Bedrag.van("0.00"));
    }

    /** Zoals de maandafsluiting (ADR 0028): zonder datum van openen geen rente. */
    @Test
    void zonderDatumVanOpenenIsDeLopendeRenteNul() {
        String spaar = spaarrekening("rente-10", 3010L, "10000.00", null);

        assertThat(rente.van("rente-10", Iban.van(spaar), LocalDate.of(2026, 2, 15))).contains(Bedrag.van("0.00"));
    }

    /** ADR 0023: andermans spaarrekening bestaat voor deze klant niet. */
    @Test
    void eenSpaarrekeningVanEenAnderHeeftGeenLopendeRente() {
        String spaar = spaarrekening("rente-11", 3011L, "10000.00", "2026-01-01");
        spaarrekening("rente-12", 3012L, "10.00", "2026-01-01");

        assertThat(rente.van("rente-12", Iban.van(spaar), LocalDate.of(2026, 2, 15))).isEmpty();
    }

    /** Alleen een spaarrekening krijgt rente (FO). */
    @Test
    void eenBetaalrekeningHeeftGeenLopendeRente() {
        String betaal = rekening("rente-13", 3013L, "betaal", "10000.00", "2026-01-01");

        assertThat(rente.van("rente-13", Iban.van(betaal), LocalDate.of(2026, 2, 15))).isEmpty();
    }

    /**
     * ADR 0030, de voorbeelden van het scenario: € 5.000,00 vanaf 1 januari, rente t/m maart bijgeschreven (12,74, 11,54
     * en 12,80). Een opname van € 1.000,00 op 15 februari maakt februari 10,39 en maart 10,25: correcties −1,15 en −2,55.
     */
    @Test
    void eenOpnameInEenAfgeslotenMaandCorrigeertDieMaandEnDeMaandenErna() {
        String spaar = spaarrekening("rente-20", 3020L, "5000.00", "2026-01-01");
        String betaal = rekening("rente-20b", 3021L, "betaal", "0.00", null);
        rente.sluitAf(LocalDate.of(2026, 3, 31));

        ledger.boek(Overboeking.opname(new Partij(Iban.van(spaar), "Jan"), new Partij(Iban.van(betaal), "Jan"),
                Bedrag.van("1000.00"), LocalDateTime.of(2026, 2, 15, 10, 0), "Opname"));

        assertThat(mutaties(spaar)).filteredOn(mutatie -> mutatie.contains("Rentecorrectie")).containsExactly(
                "2026-02-28 -1.15 RENTE BankSim Rentecorrectie februari",
                "2026-03-31 -2.55 RENTE BankSim Rentecorrectie maart");
    }

    /** Hetzelfde voorbeeld met een inleg: februari 12,69 en maart 15,35, correcties +1,15 en +2,55, van BankSim. */
    @Test
    void eenInlegInEenAfgeslotenMaandGeeftExtraRente() {
        String spaar = spaarrekening("rente-21", 3022L, "5000.00", "2026-01-01");
        String betaal = rekening("rente-21b", 3023L, "betaal", "1000.00", null);
        rente.sluitAf(LocalDate.of(2026, 3, 31));

        ledger.boek(Overboeking.inleg(new Partij(Iban.van(betaal), "Jan"), new Partij(Iban.van(spaar), "Jan"),
                Bedrag.van("1000.00"), LocalDateTime.of(2026, 2, 15, 10, 0), "Inleg"));

        assertThat(mutaties(spaar)).filteredOn(mutatie -> mutatie.contains("Rentecorrectie")).containsExactly(
                "2026-02-28 1.15 RENTE BankSim Rentecorrectie februari",
                "2026-03-31 2.55 RENTE BankSim Rentecorrectie maart");
    }

    /**
     * De rente van februari zelf (€ 692,17, geboekt op 28 februari) telt niet mee in de herberekening van februari. Met
     * € 300.000,00 scheelt dat zes cent: februari wordt 691,02, de correctie −1,15 (met de rente erin −1,09).
     */
    @Test
    void deRenteVanDeMaandZelfTeltNietMeeInDeHerberekening() {
        String spaar = spaarrekening("rente-25", 3029L, "300000.00", "2026-01-01");
        String betaal = rekening("rente-25b", 3030L, "betaal", "0.00", null);
        rente.sluitAf(LocalDate.of(2026, 2, 28));

        ledger.boek(Overboeking.opname(new Partij(Iban.van(spaar), "Jan"), new Partij(Iban.van(betaal), "Jan"),
                Bedrag.van("1000.00"), LocalDateTime.of(2026, 2, 15, 10, 0), "Opname"));

        assertThat(mutaties(spaar)).filteredOn(mutatie -> mutatie.contains("Rentecorrectie"))
                .containsExactly("2026-02-28 -1.15 RENTE BankSim Rentecorrectie februari");
    }

    /** Februari is nog niet afgesloten: die krijgt bij de afsluiting meteen de goede rente, zonder correctie. */
    @Test
    void eenMaandZonderBijschrijvingKrijgtGeenCorrectie() {
        String spaar = spaarrekening("rente-22", 3024L, "5000.00", "2026-01-01");
        String betaal = rekening("rente-22b", 3025L, "betaal", "0.00", null);
        rente.sluitAf(LocalDate.of(2026, 2, 1));

        ledger.boek(Overboeking.opname(new Partij(Iban.van(spaar), "Jan"), new Partij(Iban.van(betaal), "Jan"),
                Bedrag.van("1000.00"), LocalDateTime.of(2026, 2, 15, 10, 0), "Opname"));

        assertThat(mutaties(spaar)).noneMatch(mutatie -> mutatie.contains("Rentecorrectie"));
    }

    /** € 0,01 veertien dagen in februari en heel maart: minder dan een halve cent rente, dus geen correctie. */
    @Test
    void zonderVerschilWordtErNietsGeboekt() {
        String spaar = spaarrekening("rente-23", 3026L, "5000.00", "2026-01-01");
        String betaal = rekening("rente-23b", 3027L, "betaal", "0.00", null);
        rente.sluitAf(LocalDate.of(2026, 3, 31));

        ledger.boek(Overboeking.opname(new Partij(Iban.van(spaar), "Jan"), new Partij(Iban.van(betaal), "Jan"),
                Bedrag.van("0.01"), LocalDateTime.of(2026, 2, 15, 10, 0), "Opname"));

        assertThat(mutaties(spaar)).noneMatch(mutatie -> mutatie.contains("Rentecorrectie"));
    }

    /** Rente en correcties zijn zelf van het type RENTE: daarop volgt geen herberekening, anders corrigeert hij zichzelf. */
    @Test
    void opRenteVolgtGeenHerberekening() {
        String spaar = spaarrekening("rente-24", 3028L, "5000.00", "2026-01-01");
        rente.sluitAf(LocalDate.of(2026, 3, 31));

        ledger.boek(new Overboeking(MaandRente.BANKSIM, new Partij(Iban.van(spaar), "Jan"), Bedrag.van("1000.00"),
                TransactieType.RENTE, LocalDateTime.of(2026, 2, 15, 10, 0)));

        assertThat(mutaties(spaar)).noneMatch(mutatie -> mutatie.contains("Rentecorrectie"));
    }

    private String spaarrekening(String gebruiker, long nummer, String saldo, String geopendOp) {
        return rekening(gebruiker, nummer, "spaar", saldo, geopendOp);
    }

    private String rekening(String gebruiker, long nummer, String soort, String saldo, String geopendOp) {
        String iban = Iban.simBank(nummer).toString();
        jdbc.sql("INSERT INTO rekeninghouder (gebruikersnaam, naam) VALUES (:gebruiker, :gebruiker)").param("gebruiker", gebruiker)
                .update();
        jdbc.sql("""
                        INSERT INTO rekening (iban, rekeninghouder_id, soort, openingssaldo, geopend_op)
                        SELECT :iban, id, :soort, :saldo, :geopendOp FROM rekeninghouder WHERE gebruikersnaam = :gebruiker""")
                .param("iban", iban).param("soort", soort).param("saldo", new BigDecimal(saldo))
                .param("geopendOp", geopendOp == null ? null : LocalDate.parse(geopendOp))
                .param("gebruiker", gebruiker).update();
        return iban;
    }

    private List<String> mutaties(String iban) {
        return jdbc.sql("""
                        SELECT datum, bedrag, type, tegenpartij, omschrijving FROM transactie WHERE iban = :iban
                        ORDER BY datum""")
                .param("iban", iban)
                .query((rij, nummer) -> rij.getDate("datum") + " " + rij.getBigDecimal("bedrag").toPlainString() + " "
                        + rij.getString("type") + " " + rij.getString("tegenpartij") + " " + rij.getString("omschrijving"))
                .list();
    }
}
