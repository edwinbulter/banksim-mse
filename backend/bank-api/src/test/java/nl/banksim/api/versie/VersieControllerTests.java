package nl.banksim.api.versie;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import nl.banksim.api.beveiliging.BeveiligingConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** Ook zonder login: de voettekst en de rooktest vragen de versie op (ADR 0008). */
@WebMvcTest(controllers = VersieController.class, properties = "banksim.versie=1.2.3")
@Import(BeveiligingConfig.class)
class VersieControllerTests {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    JwtDecoder jwtDecoder;

    @Test
    void geeftDeIngesteldeVersie() throws Exception {
        mvc.perform(get("/api/versie"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.versie").value("1.2.3"));
    }
}
