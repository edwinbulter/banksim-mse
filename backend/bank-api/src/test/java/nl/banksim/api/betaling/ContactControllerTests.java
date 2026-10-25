package nl.banksim.api.betaling;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import nl.banksim.api.beveiliging.BeveiligingConfig;
import nl.banksim.api.beveiliging.IngelogdeGebruiker;
import nl.banksim.domain.grootboek.Contact;
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

/** S7.1 (FO): suggesties uit de contactenlijst bij typen in naam of IBAN op het betaalscherm. */
@WebMvcTest(ContactController.class)
@Import({BeveiligingConfig.class, IngelogdeGebruiker.class, ContactControllerTests.Opslag.class})
class ContactControllerTests {

    @Autowired
    MockMvc mvc;

    @Autowired
    GeheugenContacten contacten;

    @MockitoBean
    JwtDecoder jwtDecoder;

    @BeforeEach
    void sanne() {
        contacten.leeg();
        contacten.voegToe(new Contact(Iban.simBank(2L), "Sanne"));
    }

    @Test
    void geeftDeSuggestiesVoorDeIngelogdeGebruikerMetNaamEnIban() throws Exception {
        mvc.perform(get("/api/contacten").param("q", "San").with(jwt().jwt(token -> token.claim("preferred_username", "jan-a1-1"))))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        [{"naam": "Sanne", "iban": "NL67SIMB0000000002"}]""", JsonCompareMode.STRICT));
        org.assertj.core.api.Assertions.assertThat(contacten.gezocht()).containsExactly("jan-a1-1 San");
    }

    @TestConfiguration
    static class Opslag {

        @Bean
        GeheugenContacten contacten() {
            return new GeheugenContacten();
        }
    }
}
