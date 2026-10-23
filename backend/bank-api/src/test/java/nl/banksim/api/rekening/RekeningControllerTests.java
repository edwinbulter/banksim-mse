package nl.banksim.api.rekening;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(RekeningController.class)
@Import({BeveiligingConfig.class, IngelogdeGebruiker.class, RekeningControllerTests.Rekeningen.class})
class RekeningControllerTests {

    /** Op welke datum de controller het saldo vroeg. */
    static final List<LocalDate> GEVRAAGD = new CopyOnWriteArrayList<>();

    @Autowired
    MockMvc mvc;

    @MockitoBean
    JwtDecoder jwtDecoder;

    @Test
    void geeftDeRekeningenVanDeIngelogdeGebruikerMetSaldoAlsString() throws Exception {
        mvc.perform(get("/api/rekeningen").with(jwt().jwt(token -> token.claim("preferred_username", "jan-a1-1"))))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        [{"soort": "betaal", "iban": "NL94SIMB0000000001", "saldo": "1842.17"}]""",
                        org.springframework.test.json.JsonCompareMode.STRICT));
    }

    @Test
    void geeftGeenRekeningenVanEenAndereGebruiker() throws Exception {
        mvc.perform(get("/api/rekeningen").with(jwt().jwt(token -> token.claim("preferred_username", "piet-a1-2"))))
                .andExpect(status().isOk())
                .andExpect(content().json("[]", org.springframework.test.json.JsonCompareMode.STRICT));
    }

    /** ADR 0013: het saldo op "vandaag" volgens de SimulationClock, hier de simulatiedatum 5 oktober 2026. */
    @Test
    void vraagtHetSaldoOpDeDatumVanDeSimulationClock() throws Exception {
        GEVRAAGD.clear();
        mvc.perform(get("/api/rekeningen").with(jwt().jwt(token -> token.claim("preferred_username", "jan-a1-1"))))
                .andExpect(status().isOk());
        org.assertj.core.api.Assertions.assertThat(GEVRAAGD).containsExactly(LocalDate.of(2026, 10, 5));
    }

    @Test
    void zonderTokenIs401() throws Exception {
        mvc.perform(get("/api/rekeningen")).andExpect(status().isUnauthorized());
    }

    @TestConfiguration
    static class Rekeningen {

        @Bean
        RekeningRepository rekeningRepository() {
            return (gebruikersnaam, vandaag) -> {
                GEVRAAGD.add(vandaag);
                return "jan-a1-1".equals(gebruikersnaam)
                        ? List.of(new Rekening(Soort.BETAAL, Iban.simBank(1L), Bedrag.van("1842.17")))
                        : List.of();
            };
        }

        @Bean
        SimulationClock simulationClock() {
            var simulatiedatum = new GeheugenSimulatiedatum();
            simulatiedatum.zet(LocalDate.of(2026, 10, 5));
            return new SimulationClock(Clock.fixed(Instant.parse("2026-10-22T12:00:00Z"), ZoneId.of("UTC")), simulatiedatum);
        }
    }
}
