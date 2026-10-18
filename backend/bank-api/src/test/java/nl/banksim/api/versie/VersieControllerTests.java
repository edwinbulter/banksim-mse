package nl.banksim.api.versie;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = VersieController.class, properties = "banksim.versie=1.2.3")
class VersieControllerTests {

    @Autowired
    MockMvc mvc;

    @Test
    void geeftDeIngesteldeVersie() throws Exception {
        mvc.perform(get("/api/versie"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.versie").value("1.2.3"));
    }
}
