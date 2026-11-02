package nl.banksim.api.beheer;

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

import nl.banksim.api.ApiContract;
import nl.banksim.api.beveiliging.BeveiligingConfig;
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
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** S10.1 (FO "Admin scherm"): de beheerder zoekt rekeninghouders, met hun rekeningen en de saldi op vandaag. */
@WebMvcTest(BeheerController.class)
@Import({ApiContract.class, BeveiligingConfig.class, BeheerControllerTests.Opslag.class})
class BeheerControllerTests {

    /** Waarmee de controller zocht: de tekst en de datum. */
    static final List<String> GEZOCHT = new CopyOnWriteArrayList<>();

    @Autowired
    MockMvc mvc;

    @MockitoBean
    JwtDecoder jwtDecoder;

    @BeforeEach
    void leeg() {
        GEZOCHT.clear();
    }

    /** ADR 0010: bedragen als string; zonder spaarrekening zijn spaarrekening en spaarsaldo null. */
    @Test
    void geeftDeGevondenRekeninghoudersMetHunSaldi() throws Exception {
        mvc.perform(get("/api/beheer/rekeninghouders").param("zoek", "NL94SIMB").with(beheerder()))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        [{"naam": "Jan", "betaalrekening": "NL94SIMB0000000001", "saldo": "1842.17",
                          "spaarrekening": "NL67SIMB0000000002", "spaarsaldo": "12405.63"},
                         {"naam": "Piet", "betaalrekening": "NL40SIMB0000000003", "saldo": "5.00",
                          "spaarrekening": null, "spaarsaldo": null}]""", JsonCompareMode.STRICT));
    }

    /** ADR 0013: de saldi op "vandaag" volgens de SimulationClock, hier 5 oktober 2026. */
    @Test
    void zoektMetDeTekstOpDeDatumVanDeBank() throws Exception {
        mvc.perform(get("/api/beheer/rekeninghouders").param("zoek", "jan").with(beheerder())).andExpect(status().isOk());

        assertThat(GEZOCHT).containsExactly("jan 2026-10-05");
    }

    /** Zonder zoektekst zoekt de beheerder op niets: de eerste rekeninghouders op naam. */
    @Test
    void zonderZoektekstIsDeTekstLeeg() throws Exception {
        mvc.perform(get("/api/beheer/rekeninghouders").with(beheerder())).andExpect(status().isOk());

        assertThat(GEZOCHT).containsExactly(" 2026-10-05");
    }

    /** ADR 0022: alleen de rol beheerder. */
    @Test
    void eenKlantKrijgt403() throws Exception {
        mvc.perform(get("/api/beheer/rekeninghouders").with(jwt().jwt(token -> token.claim("preferred_username", "jan-a1-1"))))
                .andExpect(status().isForbidden());
        assertThat(GEZOCHT).isEmpty();
    }

    private static RequestPostProcessor beheerder() {
        return jwt().jwt(token -> token.claim("preferred_username", "beheerder-a1-1"))
                .authorities(new SimpleGrantedAuthority("ROLE_beheerder"));
    }

    @TestConfiguration
    static class Opslag {

        @Bean
        Rekeninghouders rekeninghouders() {
            return (tekst, vandaag) -> {
                GEZOCHT.add(tekst + " " + vandaag);
                return List.of(
                        new Rekeninghouder("Jan", Iban.van("NL94SIMB0000000001"), Bedrag.van("1842.17"),
                                Optional.of(Iban.van("NL67SIMB0000000002")), Optional.of(Bedrag.van("12405.63"))),
                        new Rekeninghouder("Piet", Iban.van("NL40SIMB0000000003"), Bedrag.van("5.00"),
                                Optional.empty(), Optional.empty()));
            };
        }

        @Bean
        SimulationClock simulationClock() {
            GeheugenSimulatiedatum simulatiedatum = new GeheugenSimulatiedatum();
            simulatiedatum.zet(LocalDate.of(2026, 10, 5));
            return new SimulationClock(Clock.fixed(Instant.parse("2026-10-22T12:00:00Z"), ZoneId.of("UTC")), simulatiedatum);
        }
    }
}
