package nl.banksim.api.beveiliging;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import nl.banksim.api.ApiContract;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;

/** ADR 0037: de frontend vraagt wie de gebruiker is, voor de navigatie; de API beslist zelf over toegang. */
@WebMvcTest(IkController.class)
@Import({ApiContract.class, BeveiligingConfig.class, IngelogdeGebruiker.class})
class IkControllerTests {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    JwtDecoder jwtDecoder;

    @Test
    void eenKlantIsGeenBeheerder() throws Exception {
        mvc.perform(get("/api/ik").with(jwt().jwt(token -> token.claim("preferred_username", "jan-a1-1"))))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        {"gebruikersnaam": "jan-a1-1", "beheerder": false}""", JsonCompareMode.STRICT));
    }

    /** ADR 0015: de realm-rol beheerder wordt het recht ROLE_beheerder. */
    @Test
    void eenBeheerderIsBeheerder() throws Exception {
        mvc.perform(get("/api/ik").with(jwt().jwt(token -> token.claim("preferred_username", "beheerder-a1-1"))
                        .authorities(new SimpleGrantedAuthority("ROLE_beheerder"))))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        {"gebruikersnaam": "beheerder-a1-1", "beheerder": true}""", JsonCompareMode.STRICT));
    }

    @Test
    void zonderTokenIs401() throws Exception {
        mvc.perform(get("/api/ik")).andExpect(status().isUnauthorized());
    }
}
