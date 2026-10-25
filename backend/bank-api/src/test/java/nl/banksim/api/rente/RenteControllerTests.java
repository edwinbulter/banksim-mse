package nl.banksim.api.rente;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

import nl.banksim.api.beveiliging.BeveiligingConfig;
import nl.banksim.api.beveiliging.IngelogdeGebruiker;
import nl.banksim.api.simulatie.GeheugenSimulatiedatum;
import nl.banksim.api.simulatie.SimulationClock;
import nl.banksim.domain.geld.Bedrag;
import nl.banksim.domain.rekening.Iban;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;

/** S8.1: de lopende rente van een eigen spaarrekening; andermans of onbekende rekening geeft 404 (ADR 0023). */
@WebMvcTest(RenteController.class)
@Import({BeveiligingConfig.class, IngelogdeGebruiker.class, RenteControllerTests.Config.class})
class RenteControllerTests {

    static final Iban SPAAR = Iban.simBank(7L);

    /** Met welke gebruikersnaam, rekening en datum de controller vroeg. */
    static final List<String> GEVRAAGD = new CopyOnWriteArrayList<>();

    @Autowired
    MockMvc mvc;

    @MockitoBean
    JwtDecoder jwtDecoder;

    @BeforeEach
    void wis() {
        GEVRAAGD.clear();
    }

    @Test
    void geeftDeLopendeRenteAlsStringOpDeDatumVanDeSimulationClock() throws Exception {
        mvc.perform(get("/api/rekeningen/" + SPAAR + "/lopende-rente").with(jan()))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        {"bedrag": "12.36"}""", JsonCompareMode.STRICT));
        assertThat(GEVRAAGD).containsExactly("jan-a1-1 " + SPAAR + " 2026-02-15");
    }

    @Test
    void eenRekeningZonderLopendeRenteVoorDezeKlantIs404() throws Exception {
        mvc.perform(get("/api/rekeningen/" + Iban.simBank(8L) + "/lopende-rente").with(jan()))
                .andExpect(status().isNotFound());
    }

    @Test
    void eenOngeldigIbanIs404() throws Exception {
        mvc.perform(get("/api/rekeningen/NL00GEEN/lopende-rente").with(jan())).andExpect(status().isNotFound());
        assertThat(GEVRAAGD).isEmpty();
    }

    @Test
    void zonderTokenIs401() throws Exception {
        mvc.perform(get("/api/rekeningen/" + SPAAR + "/lopende-rente")).andExpect(status().isUnauthorized());
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor jan() {
        return jwt().jwt(token -> token.claim("preferred_username", "jan-a1-1"));
    }

    @TestConfiguration
    static class Config {

        @Bean
        LopendeRente lopendeRente() {
            return (gebruikersnaam, iban, vandaag) -> {
                GEVRAAGD.add(gebruikersnaam + " " + iban + " " + vandaag);
                return iban.equals(SPAAR) ? Optional.of(Bedrag.van("12.36")) : Optional.empty();
            };
        }

        @Bean
        SimulationClock simulationClock() {
            var simulatiedatum = new GeheugenSimulatiedatum();
            simulatiedatum.zet(LocalDate.of(2026, 2, 15));
            return new SimulationClock(Clock.fixed(Instant.parse("2026-10-22T12:00:00Z"), ZoneId.of("UTC")), simulatiedatum);
        }
    }
}
