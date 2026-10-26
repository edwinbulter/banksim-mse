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

/**
 * S9.1 (FO "Overschrijven scherm"): geld tussen de eigen betaal- en spaarrekening, als één overboeking met een type per
 * kant (ADR 0024, 0029).
 */
@WebMvcTest(OverschrijvingController.class)
@Import({BeveiligingConfig.class, IngelogdeGebruiker.class, OverschrijfService.class, LedgerService.class,
        OverschrijvingControllerTests.Opslag.class})
class OverschrijvingControllerTests {

    static final Iban BETAAL_JAN = Iban.simBank(1L);
    static final Iban SPAAR_JAN = Iban.simBank(2L);
    static final Iban SPAAR_SANNE = Iban.simBank(3L);

    @Autowired
    MockMvc mvc;

    @Autowired
    GeheugenGrootboek grootboek;

    @Autowired
    GeheugenIdempotentie idempotentie;

    @MockitoBean
    JwtDecoder jwtDecoder;

    @BeforeEach
    void janHeeftEenBetaalEnEenSpaarrekening() {
        grootboek.leeg();
        grootboek.rekening("jan-a1-1", BETAAL_JAN, "Jan", Bedrag.van("1000.00"));
        grootboek.spaarrekening("jan-a1-1", SPAAR_JAN, "Jan", Bedrag.van("5000.00"));
        grootboek.spaarrekening("sanne-a1-2", SPAAR_SANNE, "Sanne", Bedrag.van("5000.00"));
        idempotentie.leeg();
    }

    /** FO: op de simulatiedatum met de huidige tijd; "Overschrijving" bij de betaalrekening, "Inleg" bij de spaarrekening. */
    @Test
    void inleggenBoektEenOverschrijvingMetInlegAlsTypeOpDeSpaarrekening() throws Exception {
        mvc.perform(verzoek(UUID.randomUUID().toString()).content(opdracht(BETAAL_JAN, SPAAR_JAN)))
                .andExpect(status().isCreated());

        assertThat(grootboek.geschreven()).containsExactly(Overboeking.inleg(
                new Partij(BETAAL_JAN, "Jan"), new Partij(SPAAR_JAN, "Jan"), Bedrag.van("250.00"), LocalDateTime.of(2026, 10, 5, 14, 0), "Inleg"));
    }

    /** FO: bij opnemen is de spaarrekening Van; zij krijgt een "Opname", de betaalrekening een "Overschrijving". */
    @Test
    void opnemenBoektEenOverschrijvingMetOpnameAlsTypeOpDeSpaarrekening() throws Exception {
        mvc.perform(verzoek(UUID.randomUUID().toString()).content("""
                        {"van": "%s", "naar": "%s", "bedrag": "500.00"}""".formatted(SPAAR_JAN, BETAAL_JAN)))
                .andExpect(status().isCreated());

        assertThat(grootboek.geschreven()).containsExactly(Overboeking.opname(
                new Partij(SPAAR_JAN, "Jan"), new Partij(BETAAL_JAN, "Jan"), Bedrag.van("500.00"),
                LocalDateTime.of(2026, 10, 5, 14, 0), "Opname"));
    }

    /** ADR 0025, net als bij betalen: de spaarrekening komt niet onder € 0,00; RFC 9457 met de melding van de bank. */
    @Test
    void opnemenMeerDanHetSpaarsaldoWordtGeweigerd() throws Exception {
        mvc.perform(verzoek(UUID.randomUUID().toString()).content("""
                        {"van": "%s", "naar": "%s", "bedrag": "5000.01"}""".formatted(SPAAR_JAN, BETAAL_JAN)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.type").value("urn:banksim:probleem:saldo-ontoereikend"))
                .andExpect(jsonPath("$.detail").value(
                        "Saldo ontoereikend: deze betaling zou het saldo onder € 0,00 brengen, nu of op een latere datum."));

        assertThat(grootboek.geschreven()).isEmpty();
    }

    /** ADR 0023: andermans spaarrekening bestaat voor Jan niet. */
    @Test
    void naarAndermansSpaarrekeningIs404() throws Exception {
        mvc.perform(verzoek(UUID.randomUUID().toString()).content(opdracht(BETAAL_JAN, SPAAR_SANNE)))
                .andExpect(status().isNotFound());
        assertThat(grootboek.geschreven()).isEmpty();
    }

    @Test
    void vanAndermansRekeningIs404() throws Exception {
        grootboek.rekening("sanne-a1-2", Iban.simBank(4L), "Sanne", Bedrag.van("1000.00"));

        mvc.perform(verzoek(UUID.randomUUID().toString()).content(opdracht(Iban.simBank(4L), SPAAR_JAN)))
                .andExpect(status().isNotFound());
        assertThat(grootboek.geschreven()).isEmpty();
    }

    /** ADR 0027, net als bij betalen: dezelfde sleutel boekt één keer. */
    @Test
    void dezelfdeSleutelTweeKeerBoektEenKeer() throws Exception {
        String sleutel = UUID.randomUUID().toString();
        mvc.perform(verzoek(sleutel).content(opdracht(BETAAL_JAN, SPAAR_JAN))).andExpect(status().isCreated());
        mvc.perform(verzoek(sleutel).content(opdracht(BETAAL_JAN, SPAAR_JAN))).andExpect(status().isCreated());

        assertThat(grootboek.geschreven()).hasSize(1);
    }

    @Test
    void dezelfdeSleutelMetEenAnderVerzoekWordtGeweigerd() throws Exception {
        String sleutel = UUID.randomUUID().toString();
        mvc.perform(verzoek(sleutel).content(opdracht(BETAAL_JAN, SPAAR_JAN))).andExpect(status().isCreated());
        mvc.perform(verzoek(sleutel).content(opdracht(BETAAL_JAN, SPAAR_JAN).replace("250.00", "251.00")))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.type").value("urn:banksim:probleem:sleutel-al-gebruikt"));

        assertThat(grootboek.geschreven()).hasSize(1);
    }

    @Test
    void zonderSleutelIs400() throws Exception {
        mvc.perform(post("/api/overschrijvingen").with(jwt().jwt(token -> token.claim("preferred_username", "jan-a1-1")))
                        .contentType(MediaType.APPLICATION_JSON).content(opdracht(BETAAL_JAN, SPAAR_JAN)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("urn:banksim:probleem:sleutel-ontbreekt"));
        assertThat(grootboek.geschreven()).isEmpty();
    }

    private static MockHttpServletRequestBuilder verzoek(String sleutel) {
        return post("/api/overschrijvingen").with(jwt().jwt(token -> token.claim("preferred_username", "jan-a1-1")))
                .contentType(MediaType.APPLICATION_JSON).header("Idempotency-Key", sleutel);
    }

    private static String opdracht(Iban van, Iban naar) {
        return """
                {"van": "%s", "naar": "%s", "bedrag": "250.00"}""".formatted(van, naar);
    }

    @TestConfiguration
    static class Opslag {

        @Bean
        GeheugenGrootboek grootboek() {
            return new GeheugenGrootboek();
        }

        @Bean
        GeheugenIdempotentie idempotentie() {
            return new GeheugenIdempotentie();
        }

        /** Simulatiedatum 5 oktober 2026; 12:00 UTC is 14:00 in Europe/Amsterdam. */
        @Bean
        SimulationClock simulationClock() {
            var simulatiedatum = new GeheugenSimulatiedatum();
            simulatiedatum.zet(LocalDate.of(2026, 10, 5));
            return new SimulationClock(Clock.fixed(Instant.parse("2026-10-22T12:00:00Z"), ZoneId.of("UTC")), simulatiedatum);
        }
    }
}
