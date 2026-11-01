package nl.banksim.api.betaling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import nl.banksim.api.ApiContract;
import nl.banksim.api.PostgresTestDatabase;
import nl.banksim.domain.rekening.Iban;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Backlog fase 4.3, ADR 0027: een weigering draait de hele databasetransactie terug, ook de claim op de
 * Idempotency-Key. Vanaf hetzelfde scherm, met dezelfde sleutel, kan de klant het daarna met een ander bedrag opnieuw
 * proberen. Tegen PostgreSQL met de echte services; de controllertests gebruiken een opslag zonder transactie.
 */
@SpringBootTest(properties = {
        "banksim.cursor.sleutel=test-sleutel-van-minstens-32-tekens",
        "management.server.port="})
@AutoConfigureMockMvc
@Import({ApiContract.class, PostgresTestDatabase.class})
class SleutelNaWeigeringTests {

    // Eigen rekeningen: andere tests met dezelfde Spring-context delen de database. Ook deze twee tests delen ze, dus
    // het geweigerde bedrag is altijd te hoog.
    static final String BETAAL = Iban.simBank(9001L).toString();
    static final String SPAAR = Iban.simBank(9002L).toString();
    static final String CONTACT = Iban.simBank(9003L).toString();

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcClient jdbc;

    @MockitoBean
    JwtDecoder jwtDecoder;

    @BeforeEach
    void rekeningen() {
        if (jdbc.sql("SELECT count(*) FROM rekeninghouder WHERE gebruikersnaam = 'ida-a1-1'").query(Long.class).single() > 0) {
            return;
        }
        jdbc.sql("INSERT INTO rekeninghouder (gebruikersnaam, naam) VALUES ('ida-a1-1', 'Ida'), ('ole-a1-1', 'Ole')").update();
        jdbc.sql("""
                INSERT INTO rekening (iban, rekeninghouder_id, soort, openingssaldo, geopend_op)
                SELECT :betaal, id, 'betaal', 500.00, NULL FROM rekeninghouder WHERE gebruikersnaam = 'ida-a1-1'
                UNION ALL
                SELECT :spaar, id, 'spaar', 5000.00, CURRENT_DATE FROM rekeninghouder WHERE gebruikersnaam = 'ida-a1-1'
                UNION ALL
                SELECT :contact, id, 'betaal', 0.00, NULL FROM rekeninghouder WHERE gebruikersnaam = 'ole-a1-1'
                """).param("betaal", BETAAL).param("spaar", SPAAR).param("contact", CONTACT).update();
        jdbc.sql("INSERT INTO contact (iban, naam) VALUES (:iban, 'Ole')").param("iban", CONTACT).update();
    }

    @Test
    void naEenGeweigerdeOverschrijvingKanDezelfdeSleutelOpnieuw() throws Exception {
        String sleutel = UUID.randomUUID().toString();

        mvc.perform(verzoek("/api/overschrijvingen", sleutel).content(overschrijving("99999.99")))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.type").value("urn:banksim:probleem:saldo-ontoereikend"));
        mvc.perform(verzoek("/api/overschrijvingen", sleutel).content(overschrijving("500.00")))
                .andExpect(status().isCreated());

        assertThat(aantalTransacties(SPAAR)).as("alleen de tweede poging is geboekt").isEqualTo(1);
    }

    @Test
    void naEenGeweigerdeBetalingKanDezelfdeSleutelOpnieuw() throws Exception {
        String sleutel = UUID.randomUUID().toString();

        mvc.perform(verzoek("/api/betalingen", sleutel).content(betaling("99999.99")))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.type").value("urn:banksim:probleem:saldo-ontoereikend"));
        mvc.perform(verzoek("/api/betalingen", sleutel).content(betaling("25.00")))
                .andExpect(status().isCreated());

        assertThat(aantalTransacties(CONTACT)).as("alleen de tweede poging is geboekt").isEqualTo(1);
    }

    private long aantalTransacties(String iban) {
        return jdbc.sql("SELECT count(*) FROM transactie WHERE iban = :iban").param("iban", iban).query(Long.class).single();
    }

    private static MockHttpServletRequestBuilder verzoek(String pad, String sleutel) {
        return post(pad).with(jwt().jwt(token -> token.claim("preferred_username", "ida-a1-1")))
                .contentType(MediaType.APPLICATION_JSON).header("Idempotency-Key", sleutel);
    }

    private static String overschrijving(String bedrag) {
        return """
                {"van": "%s", "naar": "%s", "bedrag": "%s"}""".formatted(SPAAR, BETAAL, bedrag);
    }

    private static String betaling(String bedrag) {
        return """
                {"van": "%s", "naar": "%s", "naam": "Ole", "bedrag": "%s"}""".formatted(BETAAL, CONTACT, bedrag);
    }
}
