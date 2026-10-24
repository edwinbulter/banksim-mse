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

    @MockitoBean
    JwtDecoder jwtDecoder;

    @BeforeEach
    void janHeeftEenRekening() {
        grootboek.leeg();
        grootboek.rekening("jan-a1-1", VAN_JAN, "Jan");
        contacten.leeg();
        contacten.voegToe(new Contact(VAN_SANNE, "Sanne"));
    }

    /** FO: op de simulatiedatum (5 oktober) met de huidige tijd (14:00 in Europe/Amsterdam), type Online bankieren. */
    @Test
    void boektDeBetalingAlsOverboekingOpVandaagVoorDeBankMetDeHuidigeTijd() throws Exception {
        mvc.perform(post("/api/betalingen").with(jan()).contentType(MediaType.APPLICATION_JSON).content(betaling(VAN_JAN)))
                .andExpect(status().isCreated());

        assertThat(grootboek.geschreven()).containsExactly(new Overboeking(
                new Partij(VAN_JAN, "Jan"), new Partij(VAN_SANNE, "Sanne"), Bedrag.van("25.00"),
                TransactieType.ONLINE_BANKIEREN, LocalDateTime.of(2026, 10, 5, 14, 0)));
    }

    /** ADR 0023: van andermans rekening bestaat voor de klant niet. */
    @Test
    void vanEenRekeningVanEenAnderGeeft404EnBoektNiets() throws Exception {
        grootboek.rekening("piet-a1-2", Iban.simBank(3L), "Piet");

        mvc.perform(post("/api/betalingen").with(jan()).contentType(MediaType.APPLICATION_JSON)
                        .content(betaling(Iban.simBank(3L))))
                .andExpect(status().isNotFound());

        assertThat(grootboek.geschreven()).isEmpty();
    }

    /** S7.1: alleen naar een IBAN uit de contactenlijst van de bank; RFC 9457 met het probleemtype. */
    @Test
    void naarEenIbanDatGeenContactIsGeeft422EnBoektNiets() throws Exception {
        contacten.leeg();

        mvc.perform(post("/api/betalingen").with(jan()).contentType(MediaType.APPLICATION_JSON).content(betaling(VAN_JAN)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.type").value("urn:banksim:probleem:onbekend-contact"))
                .andExpect(jsonPath("$.detail").value("Naar dit rekeningnummer kan niet worden betaald."));

        assertThat(grootboek.geschreven()).isEmpty();
    }

    /** FO: de naam moet bij het contact passen; RFC 9457 met het probleemtype. */
    @Test
    void eenNaamDieNietBijHetContactPastGeeft422EnBoektNiets() throws Exception {
        mvc.perform(post("/api/betalingen").with(jan()).contentType(MediaType.APPLICATION_JSON)
                        .content(betaling(VAN_JAN, "Piet")))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.type").value("urn:banksim:probleem:naam-past-niet"))
                .andExpect(jsonPath("$.detail").value("De naam van de ontvanger hoort niet bij dit rekeningnummer."));

        assertThat(grootboek.geschreven()).isEmpty();
    }

    /** Jan ziet de naam van het contact als tegenpartij, niet wat hij typte. */
    @Test
    void boektMetDeNaamVanHetContactOokAlsDeKlantHemAndersSchreef() throws Exception {
        mvc.perform(post("/api/betalingen").with(jan()).contentType(MediaType.APPLICATION_JSON)
                        .content(betaling(VAN_JAN, "  sanne ")))
                .andExpect(status().isCreated());

        assertThat(grootboek.geschreven()).extracting(overboeking -> overboeking.naar().naam()).containsExactly("Sanne");
    }

    private static String betaling(Iban van) {
        return betaling(van, "Sanne");
    }

    private static String betaling(Iban van, String naam) {
        return """
                {"van": "%s", "naar": "%s", "naam": "%s", "bedrag": "25.00"}""".formatted(van, VAN_SANNE, naam);
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
        SimulationClock simulationClock() {
            var simulatiedatum = new GeheugenSimulatiedatum();
            simulatiedatum.zet(LocalDate.of(2026, 10, 5));
            return new SimulationClock(Clock.fixed(Instant.parse("2026-10-22T12:00:00Z"), ZoneId.of("UTC")), simulatiedatum);
        }
    }
}
