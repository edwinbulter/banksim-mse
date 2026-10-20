package nl.banksim.api.rekening;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import nl.banksim.api.beveiliging.BeveiligingConfig;
import nl.banksim.api.beveiliging.IngelogdeGebruiker;
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

    @Test
    void zonderTokenIs401() throws Exception {
        mvc.perform(get("/api/rekeningen")).andExpect(status().isUnauthorized());
    }

    @TestConfiguration
    static class Rekeningen {

        @Bean
        RekeningRepository rekeningRepository() {
            return gebruikersnaam -> "jan-a1-1".equals(gebruikersnaam)
                    ? List.of(new Rekening(Soort.BETAAL, Iban.simBank(1L), Bedrag.van("1842.17")))
                    : List.of();
        }
    }
}
