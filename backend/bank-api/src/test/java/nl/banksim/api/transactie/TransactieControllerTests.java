package nl.banksim.api.transactie;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

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
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;

/** S2.1: de transacties van een eigen rekening, zoals de repository ze aanlevert (nieuw naar oud). */
@WebMvcTest(TransactieController.class)
@Import({BeveiligingConfig.class, IngelogdeGebruiker.class, TransactieControllerTests.Transacties.class})
class TransactieControllerTests {

    static final Iban VAN_JAN = Iban.simBank(1L);

    @Autowired
    MockMvc mvc;

    @MockitoBean
    JwtDecoder jwtDecoder;

    @Test
    void geeftDeTransactiesVanDeEigenRekeningMetDatumEnBedragAlsString() throws Exception {
        mvc.perform(get("/api/rekeningen/" + VAN_JAN + "/transacties").with(gebruiker("jan-a1-1")))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        [{"datum": "2026-10-02", "tegenpartij": "Werkgever B.V.", "bedrag": "3215.00"},
                         {"datum": "2026-10-01", "tegenpartij": "Woningstichting Thuis", "bedrag": "-812.40"}]""",
                        JsonCompareMode.STRICT));
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

        @Bean
        TransactieRepository transactieRepository() {
            return (gebruikersnaam, iban) -> "jan-a1-1".equals(gebruikersnaam) && VAN_JAN.equals(iban)
                    ? Optional.of(List.of(
                            new Transactie(LocalDate.of(2026, 10, 2), "Werkgever B.V.", Bedrag.van("3215.00")),
                            new Transactie(LocalDate.of(2026, 10, 1), "Woningstichting Thuis", Bedrag.van("-812.40"))))
                    : Optional.empty();
        }
    }
}
