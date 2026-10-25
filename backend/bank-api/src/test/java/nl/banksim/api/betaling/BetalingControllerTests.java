package nl.banksim.api.betaling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;

import nl.banksim.api.beveiliging.BeveiligingConfig;
import nl.banksim.api.beveiliging.IngelogdeGebruiker;
import nl.banksim.api.grootboek.GeheugenGrootboek;
import nl.banksim.api.grootboek.LedgerService;
import nl.banksim.api.simulatie.GeheugenSimulatiedatum;
import nl.banksim.api.simulatie.SimulationClock;
import nl.banksim.domain.geld.Bedrag;
import nl.banksim.domain.grootboek.Contact;
import nl.banksim.domain.grootboek.Overboeking;
import nl.banksim.domain.grootboek.Partij;
import nl.banksim.domain.rekening.Iban;
import nl.banksim.domain.transactie.TransactieType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** S7.1: een betaling is één overboeking van de eigen rekening naar de ontvanger (ADR 0024). */
@WebMvcTest(BetalingController.class)
@Import({BeveiligingConfig.class, IngelogdeGebruiker.class, BetaalService.class, LedgerService.class,
        BetalingControllerTests.Opslag.class})
class BetalingControllerTests {

    static final Iban VAN_JAN = Iban.simBank(1L);
    static final Iban VAN_SANNE = Iban.simBank(2L);

    @Autowired
    MockMvc mvc;

    @Autowired
    GeheugenGrootboek grootboek;

    @Autowired
    GeheugenContacten contacten;

    @Autowired
    GeheugenIdempotentie idempotentie;

    @MockitoBean
    JwtDecoder jwtDecoder;

    @BeforeEach
    void janHeeftEenRekening() {
        grootboek.leeg();
        grootboek.rekening("jan-a1-1", VAN_JAN, "Jan", Bedrag.van("500.00"));
        contacten.leeg();
        contacten.voegToe(new Contact(VAN_SANNE, "Sanne"));
        idempotentie.leeg();
    }

    /** FO: op de simulatiedatum (5 oktober) met de huidige tijd (14:00 in Europe/Amsterdam), type Online bankieren. */
    @Test
    void boektDeBetalingAlsOverboekingOpVandaagVoorDeBankMetDeHuidigeTijd() throws Exception {
        mvc.perform(betaalverzoek().content(betaling(VAN_JAN)))
                .andExpect(status().isCreated());

        assertThat(grootboek.geschreven()).containsExactly(new Overboeking(
                new Partij(VAN_JAN, "Jan"), new Partij(VAN_SANNE, "Sanne"), Bedrag.van("25.00"),
                TransactieType.ONLINE_BANKIEREN, LocalDateTime.of(2026, 10, 5, 14, 0)));
    }

    /** FO: omschrijving en betalingskenmerk zijn optioneel en gaan mee in de overboeking. */
    @Test
    void neemtOmschrijvingEnBetalingskenmerkMeeInDeOverboeking() throws Exception {
        mvc.perform(betaalverzoek().content("""
                        {"van": "%s", "naar": "%s", "naam": "Sanne", "bedrag": "25.00",
                         "omschrijving": "Etentje", "betalingskenmerk": "ETEN-2026"}""".formatted(VAN_JAN, VAN_SANNE)))
                .andExpect(status().isCreated());

        assertThat(grootboek.geschreven()).extracting(Overboeking::omschrijving, Overboeking::betalingskenmerk)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("Etentje", "ETEN-2026"));
    }

    /** FO: de extra omschrijving is optioneel en gaat mee in de overboeking; getoond wordt ze niet. */
    @Test
    void neemtDeExtraOmschrijvingMeeInDeOverboeking() throws Exception {
        mvc.perform(betaalverzoek().content("""
                        {"van": "%s", "naar": "%s", "naam": "Sanne", "bedrag": "25.00", "extraOmschrijving": "Voor de pizza"}"""
                        .formatted(VAN_JAN, VAN_SANNE)))
                .andExpect(status().isCreated());

        assertThat(grootboek.geschreven()).extracting(Overboeking::extraOmschrijving).containsExactly("Voor de pizza");
    }

    /** ADR 0023: van andermans rekening bestaat voor de klant niet. */
    @Test
    void vanEenRekeningVanEenAnderGeeft404EnBoektNiets() throws Exception {
        grootboek.rekening("piet-a1-2", Iban.simBank(3L), "Piet", Bedrag.van("500.00"));

        mvc.perform(betaalverzoek()
                        .content(betaling(Iban.simBank(3L))))
                .andExpect(status().isNotFound());

        assertThat(grootboek.geschreven()).isEmpty();
    }

    /** S7.1: alleen naar een IBAN uit de contactenlijst van de bank; RFC 9457 met het probleemtype. */
    @Test
    void naarEenIbanDatGeenContactIsGeeft422EnBoektNiets() throws Exception {
        contacten.leeg();

        mvc.perform(betaalverzoek().content(betaling(VAN_JAN)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.type").value("urn:banksim:probleem:onbekend-contact"))
                .andExpect(jsonPath("$.detail").value("Naar dit rekeningnummer kan niet worden betaald."));

        assertThat(grootboek.geschreven()).isEmpty();
    }

    /** FO: de naam moet bij het contact passen; RFC 9457 met het probleemtype. */
    @Test
    void eenNaamDieNietBijHetContactPastGeeft422EnBoektNiets() throws Exception {
        mvc.perform(betaalverzoek()
                        .content(betaling(VAN_JAN, "Piet")))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.type").value("urn:banksim:probleem:naam-past-niet"))
                .andExpect(jsonPath("$.detail").value("De naam van de ontvanger hoort niet bij dit rekeningnummer."));

        assertThat(grootboek.geschreven()).isEmpty();
    }

    /** Jan ziet de naam van het contact als tegenpartij, niet wat hij typte. */
    @Test
    void boektMetDeNaamVanHetContactOokAlsDeKlantHemAndersSchreef() throws Exception {
        mvc.perform(betaalverzoek()
                        .content(betaling(VAN_JAN, "  sanne ")))
                .andExpect(status().isCreated());

        assertThat(grootboek.geschreven()).extracting(overboeking -> overboeking.naar().naam()).containsExactly("Sanne");
    }

    /** Nooit rood: het saldo op de uitvoerdatum (5 oktober, de simulatiedatum) moet de betaling dekken. */
    @Test
    void meerDanHetSaldoGeeft422EnBoektNiets() throws Exception {
        mvc.perform(betaalverzoek()
                        .content(betaling(VAN_JAN, "Sanne", "500.01")))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.type").value("urn:banksim:probleem:saldo-ontoereikend"))
                .andExpect(jsonPath("$.detail").value("Saldo ontoereikend: deze betaling zou het saldo onder € 0,00 brengen, nu of op een latere datum."));

        assertThat(grootboek.geschreven()).isEmpty();
        assertThat(grootboek.saldoGevraagd()).containsExactly(VAN_JAN + " 2026-10-05");
    }

    /** ADR 0025: een incasso na de uitvoerdatum verlaagt wat er nu betaald mag worden (€ 500,00 - € 450,00). */
    @Test
    void eenLatereAfschrijvingDieRoodZouGevenGeeft422() throws Exception {
        grootboek.latereBewegingen(VAN_JAN, Bedrag.van("-450.00"));

        mvc.perform(betaalverzoek()
                        .content(betaling(VAN_JAN, "Sanne", "100.00")))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.type").value("urn:banksim:probleem:saldo-ontoereikend"));

        assertThat(grootboek.geschreven()).isEmpty();
    }

    /** Precies het saldo mag: daarna is het saldo € 0,00, niet onder nul. */
    @Test
    void preciesHetSaldoMag() throws Exception {
        mvc.perform(betaalverzoek()
                        .content(betaling(VAN_JAN, "Sanne", "500.00")))
                .andExpect(status().isCreated());

        assertThat(grootboek.geschreven()).hasSize(1);
    }

    /** ADR 0027: dezelfde sleutel twee keer is één betaling; de herhaling krijgt hetzelfde antwoord. */
    @Test
    void tweeKeerDezelfdeSleutelBoektEenKeer() throws Exception {
        String sleutel = UUID.randomUUID().toString();
        for (int keer = 0; keer < 2; keer++) {
            mvc.perform(betaalverzoek(sleutel).content(betaling(VAN_JAN))).andExpect(status().isCreated());
        }

        assertThat(grootboek.geschreven()).hasSize(1);
    }

    @Test
    void zonderSleutelGeeft400EnBoektNiets() throws Exception {
        mvc.perform(post("/api/betalingen").with(jan()).contentType(MediaType.APPLICATION_JSON).content(betaling(VAN_JAN)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("urn:banksim:probleem:sleutel-ontbreekt"));

        assertThat(grootboek.geschreven()).isEmpty();
    }

    /** Dezelfde sleutel met een ander verzoek is geen herhaling, maar een fout van de client. */
    @Test
    void dezelfdeSleutelMetEenAnderBedragGeeft422() throws Exception {
        String sleutel = UUID.randomUUID().toString();
        mvc.perform(betaalverzoek(sleutel).content(betaling(VAN_JAN, "Sanne", "25.00"))).andExpect(status().isCreated());

        mvc.perform(betaalverzoek(sleutel).content(betaling(VAN_JAN, "Sanne", "30.00")))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.type").value("urn:banksim:probleem:sleutel-al-gebruikt"));

        assertThat(grootboek.geschreven()).hasSize(1);
    }

    /** Een betaalverzoek van Jan, met een eigen Idempotency-Key (ADR 0027). */
    private static MockHttpServletRequestBuilder betaalverzoek() {
        return betaalverzoek(UUID.randomUUID().toString());
    }

    private static MockHttpServletRequestBuilder betaalverzoek(String sleutel) {
        return post("/api/betalingen").with(jan()).contentType(MediaType.APPLICATION_JSON).header("Idempotency-Key", sleutel);
    }

    private static String betaling(Iban van) {
        return betaling(van, "Sanne");
    }

    private static String betaling(Iban van, String naam) {
        return betaling(van, naam, "25.00");
    }

    private static String betaling(Iban van, String naam, String bedrag) {
        return """
                {"van": "%s", "naar": "%s", "naam": "%s", "bedrag": "%s"}""".formatted(van, VAN_SANNE, naam, bedrag);
    }

    private static RequestPostProcessor jan() {
        return jwt().jwt(token -> token.claim("preferred_username", "jan-a1-1"));
    }

    @TestConfiguration
    static class Opslag {

        @Bean
        GeheugenGrootboek grootboek() {
            return new GeheugenGrootboek();
        }

        @Bean
        GeheugenContacten contacten() {
            return new GeheugenContacten();
        }

        @Bean
        GeheugenIdempotentie idempotentie() {
            return new GeheugenIdempotentie();
        }

        @Bean
        SimulationClock simulationClock() {
            var simulatiedatum = new GeheugenSimulatiedatum();
            simulatiedatum.zet(LocalDate.of(2026, 10, 5));
            return new SimulationClock(Clock.fixed(Instant.parse("2026-10-22T12:00:00Z"), ZoneId.of("UTC")), simulatiedatum);
        }
    }
}
