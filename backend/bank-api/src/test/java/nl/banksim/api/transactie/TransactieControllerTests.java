package nl.banksim.api.transactie;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import nl.banksim.api.beveiliging.BeveiligingConfig;
import nl.banksim.api.beveiliging.IngelogdeGebruiker;
import nl.banksim.api.simulatie.GeheugenSimulatiedatum;
import nl.banksim.api.simulatie.SimulationClock;
import nl.banksim.domain.geld.Bedrag;
import nl.banksim.domain.rekening.Iban;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;

/**
 * S2.1 en S2.2: de transacties van een eigen rekening, per pagina van 50, zoals de repository ze aanlevert (nieuw naar
 * oud). De cursor naar de volgende pagina is ondertekend (ADR 0011).
 */
@WebMvcTest(TransactieController.class)
@Import({BeveiligingConfig.class, IngelogdeGebruiker.class, Cursor.class, TransactieControllerTests.Transacties.class})
@TestPropertySource(properties = "banksim.cursor.sleutel=" + TransactieControllerTests.SLEUTEL)
class TransactieControllerTests {

    static final String SLEUTEL = "test-sleutel-van-minstens-32-tekens";
    static final Iban VAN_JAN = Iban.simBank(1L);
    static final Iban OOK_VAN_JAN = Iban.simBank(2L);
    static final Positie LAATSTE_OP_PAGINA = new Positie(LocalDate.of(2026, 10, 1), 51L);

    /** Met welke positie en welk aantal de controller de repository aanriep. */
    static final List<String> AANROEPEN = new ArrayList<>();

    @Autowired
    MockMvc mvc;

    @MockitoBean
    JwtDecoder jwtDecoder;

    @Autowired
    Cursor cursor;

    @Test
    void geeftDeEerstePaginaVan50MetDatumEnBedragAlsStringEnZonderVolgendeAanHetEind() throws Exception {
        AANROEPEN.clear();
        mvc.perform(get("/api/rekeningen/" + OOK_VAN_JAN + "/transacties").with(gebruiker("jan-a1-1")))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        {"transacties": [
                           {"datum": "2026-10-02", "tegenpartij": "Werkgever B.V.", "bedrag": "3215.00"},
                           {"datum": "2026-10-01", "tegenpartij": "Woningstichting Thuis", "bedrag": "-812.40"}],
                         "volgende": null}""",
                        JsonCompareMode.STRICT));
        assertThat(AANROEPEN).containsExactly(OOK_VAN_JAN + " tot 2026-10-05 vanaf het begin, 50");
    }

    @Test
    void deCursorUitVolgendeVraagtDePaginaNaDeLaatsteTransactie() throws Exception {
        String volgende = mvc.perform(get("/api/rekeningen/" + VAN_JAN + "/transacties").with(gebruiker("jan-a1-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.volgende").isString())
                .andReturn().getResponse().getContentAsString().replaceAll(".*\"volgende\":\"([^\"]+)\".*", "$1");
        AANROEPEN.clear();

        mvc.perform(get("/api/rekeningen/" + VAN_JAN + "/transacties").param("na", volgende).with(gebruiker("jan-a1-1")))
                .andExpect(status().isOk());

        assertThat(AANROEPEN).containsExactly(VAN_JAN + " tot 2026-10-05 na " + LAATSTE_OP_PAGINA + ", 50");
    }

    @Test
    void eenOnleesbareCursorGeeft400() throws Exception {
        mvc.perform(get("/api/rekeningen/" + VAN_JAN + "/transacties").param("na", "geen-cursor").with(gebruiker("jan-a1-1")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void eenCursorVanEenAndereRekeningGeeft400() throws Exception {
        String vanDeAndereRekening = cursor.maak(OOK_VAN_JAN, LAATSTE_OP_PAGINA);
        mvc.perform(get("/api/rekeningen/" + VAN_JAN + "/transacties").param("na", vanDeAndereRekening)
                        .with(gebruiker("jan-a1-1")))
                .andExpect(status().isBadRequest());
    }

    /** Geen regel uit het scenario, wel uit S6.1: een rekening van een ander bestaat voor jou niet. */
    @Test
    void eenRekeningVanEenAnderGeeft404() throws Exception {
        mvc.perform(get("/api/rekeningen/" + VAN_JAN + "/transacties").with(gebruiker("piet-a1-2")))
                .andExpect(status().isNotFound());
    }

    @Test
    void eenOngeldigIbanGeeft404() throws Exception {
        mvc.perform(get("/api/rekeningen/geen-iban/transacties").with(gebruiker("jan-a1-1")))
                .andExpect(status().isNotFound());
    }

    @Test
    void zonderTokenIs401() throws Exception {
        mvc.perform(get("/api/rekeningen/" + VAN_JAN + "/transacties")).andExpect(status().isUnauthorized());
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor gebruiker(String naam) {
        return jwt().jwt(token -> token.claim("preferred_username", naam));
    }

    @TestConfiguration
    static class Transacties {

        /** ADR 0013: de transacties tot en met "vandaag" volgens de SimulationClock, hier 5 oktober 2026. */
        @Bean
        SimulationClock simulationClock() {
            var simulatiedatum = new GeheugenSimulatiedatum();
            simulatiedatum.zet(LocalDate.of(2026, 10, 5));
            return new SimulationClock(Clock.fixed(Instant.parse("2026-10-22T12:00:00Z"), ZoneId.of("UTC")), simulatiedatum);
        }

        @Bean
        TransactieRepository transactieRepository() {
            // Op VAN_JAN volgt nog een pagina, op OOK_VAN_JAN niet.
            return (gebruikersnaam, iban, vandaag, na, aantal) -> {
                AANROEPEN.add(iban + " tot " + vandaag + na.map(positie -> " na " + positie).orElse(" vanaf het begin")
                        + ", " + aantal);
                if (!"jan-a1-1".equals(gebruikersnaam) || !(VAN_JAN.equals(iban) || OOK_VAN_JAN.equals(iban))) {
                    return Optional.empty();
                }
                return Optional.of(new Pagina(List.of(
                        new Transactie(LocalDate.of(2026, 10, 2), "Werkgever B.V.", Bedrag.van("3215.00")),
                        new Transactie(LocalDate.of(2026, 10, 1), "Woningstichting Thuis", Bedrag.van("-812.40"))),
                        VAN_JAN.equals(iban) ? Optional.of(LAATSTE_OP_PAGINA) : Optional.empty()));
            };
        }
    }
}
