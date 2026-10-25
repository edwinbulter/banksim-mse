package nl.banksim.api.simulatie;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;

import nl.banksim.api.beveiliging.BeveiligingConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** S3.1, ADR 0013: de beheerder zet of wist de simulatiedatum. */
@WebMvcTest(SimulatiedatumController.class)
@Import({BeveiligingConfig.class, SimulatiedatumControllerTests.Opslag.class})
class SimulatiedatumControllerTests {

    @Autowired
    MockMvc mvc;

    @Autowired
    GeheugenSimulatiedatum opgeslagen;

    @Autowired
    Opslag.Afsluitingen afgesloten;

    @MockitoBean
    JwtDecoder jwtDecoder;

    @BeforeEach
    void leeg() {
        opgeslagen.wis();
        afgesloten.clear();
    }

    /** ADR 0028: na het zetten sluit de bank de maanden af tot en met de nieuwe datum. */
    @Test
    void naHetZettenSluitDeBankDeMaandenAfTotEnMetDeNieuweDatum() throws Exception {
        mvc.perform(put("/api/beheer/simulatiedatum").with(beheerder())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"datum\": \"2026-02-01\"}"))
                .andExpect(status().isNoContent());

        assertThat(afgesloten).containsExactly(LocalDate.of(2026, 2, 1));
    }

    /** Na het wissen draait de bank weer op vandaag, hier 22 oktober 2026 (de vaste klok). */
    @Test
    void naHetWissenSluitDeBankDeMaandenAfTotEnMetVandaag() throws Exception {
        opgeslagen.zet(LocalDate.of(2026, 2, 1));

        mvc.perform(delete("/api/beheer/simulatiedatum").with(beheerder())).andExpect(status().isNoContent());

        assertThat(afgesloten).containsExactly(LocalDate.of(2026, 10, 22));
    }

    @Test
    void eenGeweigerdeDatumSluitNietsAf() throws Exception {
        mvc.perform(put("/api/beheer/simulatiedatum").with(klant())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"datum\": \"2026-02-01\"}"))
                .andExpect(status().isForbidden());

        assertThat(afgesloten).isEmpty();
    }

    @Test
    void zetDeSimulatiedatum() throws Exception {
        mvc.perform(put("/api/beheer/simulatiedatum").with(beheerder())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"datum\": \"2026-10-05\"}"))
                .andExpect(status().isNoContent());

        assertThat(opgeslagen.lees()).contains(LocalDate.of(2026, 10, 5));
    }

    @Test
    void wistDeSimulatiedatum() throws Exception {
        opgeslagen.zet(LocalDate.of(2026, 10, 5));

        mvc.perform(delete("/api/beheer/simulatiedatum").with(beheerder())).andExpect(status().isNoContent());

        assertThat(opgeslagen.lees()).isEmpty();
    }

    @Test
    void eenOngeldigeDatumGeeft400EnVerandertNiets() throws Exception {
        mvc.perform(put("/api/beheer/simulatiedatum").with(beheerder())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"datum\": \"5 oktober\"}"))
                .andExpect(status().isBadRequest());

        assertThat(opgeslagen.lees()).isEmpty();
    }

    @Test
    void zonderDatumGeeft400() throws Exception {
        mvc.perform(put("/api/beheer/simulatiedatum").with(beheerder())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void zonderTokenIs401() throws Exception {
        mvc.perform(put("/api/beheer/simulatiedatum")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"datum\": \"2026-10-05\"}"))
                .andExpect(status().isUnauthorized());
        assertThat(opgeslagen.lees()).isEmpty();
    }

    /** ADR 0015: een klant heeft de rol beheerder niet. */
    @Test
    void eenKlantZonderDeRolBeheerderKrijgt403EnVerandertNiets() throws Exception {
        mvc.perform(put("/api/beheer/simulatiedatum").with(klant())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"datum\": \"2026-10-05\"}"))
                .andExpect(status().isForbidden());
        assertThat(opgeslagen.lees()).isEmpty();
    }

    @Test
    void eenKlantKanDeSimulatiedatumOokNietWissen() throws Exception {
        opgeslagen.zet(LocalDate.of(2026, 10, 5));

        mvc.perform(delete("/api/beheer/simulatiedatum").with(klant())).andExpect(status().isForbidden());

        assertThat(opgeslagen.lees()).contains(LocalDate.of(2026, 10, 5));
    }

    private static RequestPostProcessor beheerder() {
        return jwt().jwt(token -> token.claim("preferred_username", "beheerder-a1-1"))
                .authorities(new SimpleGrantedAuthority("ROLE_beheerder"));
    }

    private static RequestPostProcessor klant() {
        return jwt().jwt(token -> token.claim("preferred_username", "jan-a1-1"));
    }

    @TestConfiguration
    static class Opslag {

        /** De datums waarop de maandafsluiting is aangeroepen. */
        static class Afsluitingen extends java.util.concurrent.CopyOnWriteArrayList<LocalDate> {
        }

        @Bean
        GeheugenSimulatiedatum simulatiedatum() {
            return new GeheugenSimulatiedatum();
        }

        @Bean
        Afsluitingen afsluitingen() {
            return new Afsluitingen();
        }

        @Bean
        Maandafsluiting maandafsluiting(Afsluitingen afsluitingen) {
            return afsluitingen::add;
        }

        @Bean
        SimulationClock simulationClock(GeheugenSimulatiedatum simulatiedatum) {
            return new SimulationClock(java.time.Clock.fixed(java.time.Instant.parse("2026-10-22T12:00:00Z"),
                    java.time.ZoneId.of("UTC")), simulatiedatum);
        }
    }
}
