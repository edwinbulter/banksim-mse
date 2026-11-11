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
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

import nl.banksim.api.ApiContract;
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
@Import({ApiContract.class, BeveiligingConfig.class, IngelogdeGebruiker.class, RekeningControllerTests.Rekeningen.class})
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

    /** FO "Kopgedeelte": de eigen rekening met de naam van de rekeninghouder, en het saldo op de simulatiedatum. */
    @Test
    void geeftEenEigenRekeningMetDeNaamVanDeRekeninghouder() throws Exception {
        GEVRAAGD.clear();
        mvc.perform(get("/api/rekeningen/" + Iban.simBank(1L)).with(jan()))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        {"soort": "betaal", "iban": "NL94SIMB0000000001", "saldo": "1842.17", "rekeninghouder": "Jan de Vries"}""",
                        org.springframework.test.json.JsonCompareMode.STRICT));
        org.assertj.core.api.Assertions.assertThat(GEVRAAGD).containsExactly(LocalDate.of(2026, 10, 5));
    }

    /** ADR 0023: andermans rekening bestaat voor de klant niet. */
    @Test
    void eenRekeningVanEenAnderIs404() throws Exception {
        mvc.perform(get("/api/rekeningen/" + Iban.simBank(1L)).with(jwt().jwt(token -> token.claim("preferred_username", "piet-a1-2"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void eenOngeldigIbanIs404() throws Exception {
        mvc.perform(get("/api/rekeningen/NL00GEEN").with(jan())).andExpect(status().isNotFound());
    }

    @Test
    void eenRekeningZonderTokenIs401() throws Exception {
        mvc.perform(get("/api/rekeningen/" + Iban.simBank(1L))).andExpect(status().isUnauthorized());
    }

    /** ADR 0038: de beheerder leest elke rekening van een klant, alleen-lezen, ook op de simulatiedatum. */
    @Test
    void deBeheerderLeestElkeRekeningVanEenKlant() throws Exception {
        GEVRAAGD.clear();
        mvc.perform(get("/api/beheer/rekeningen/" + Iban.simBank(1L)).with(beheerder()))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        {"soort": "betaal", "iban": "NL94SIMB0000000001", "saldo": "1842.17", "rekeninghouder": "Jan de Vries"}""",
                        org.springframework.test.json.JsonCompareMode.STRICT));
        org.assertj.core.api.Assertions.assertThat(GEVRAAGD).containsExactly(LocalDate.of(2026, 10, 5));
    }

    @Test
    void eenOnbekendeRekeningInBeheerIs404() throws Exception {
        mvc.perform(get("/api/beheer/rekeningen/" + Iban.simBank(8L)).with(beheerder())).andExpect(status().isNotFound());
    }

    @Test
    void eenKlantKrijgtEenRekeningInBeheerNiet() throws Exception {
        mvc.perform(get("/api/beheer/rekeningen/" + Iban.simBank(1L)).with(jan())).andExpect(status().isForbidden());
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor jan() {
        return jwt().jwt(token -> token.claim("preferred_username", "jan-a1-1"));
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor beheerder() {
        return jwt().jwt(token -> token.claim("preferred_username", "beheerder-a1-1"))
                .authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_beheerder"));
    }

    @TestConfiguration
    static class Rekeningen {

        @Bean
        RekeningRepository rekeningRepository() {
            var jan = new RekeningMetHouder(Soort.BETAAL, Iban.simBank(1L), Bedrag.van("1842.17"), "Jan de Vries");
            return new RekeningRepository() {
                @Override
                public List<Rekening> vanGebruiker(String gebruikersnaam, LocalDate vandaag) {
                    GEVRAAGD.add(vandaag);
                    return "jan-a1-1".equals(gebruikersnaam)
                            ? List.of(new Rekening(Soort.BETAAL, Iban.simBank(1L), Bedrag.van("1842.17")))
                            : List.of();
                }

                @Override
                public Optional<RekeningMetHouder> vanGebruiker(String gebruikersnaam, Iban iban, LocalDate vandaag) {
                    GEVRAAGD.add(vandaag);
                    return "jan-a1-1".equals(gebruikersnaam) && iban.equals(jan.iban()) ? Optional.of(jan) : Optional.empty();
                }

                @Override
                public Optional<RekeningMetHouder> inBeheer(Iban iban, LocalDate vandaag) {
                    GEVRAAGD.add(vandaag);
                    return iban.equals(jan.iban()) ? Optional.of(jan) : Optional.empty();
                }
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
