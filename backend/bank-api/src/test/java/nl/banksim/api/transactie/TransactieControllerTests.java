package nl.banksim.api.transactie;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import nl.banksim.api.beveiliging.BeveiligingConfig;
import nl.banksim.api.beveiliging.IngelogdeGebruiker;
import nl.banksim.api.simulatie.GeheugenSimulatiedatum;
import nl.banksim.api.simulatie.SimulationClock;
import nl.banksim.domain.geld.Bedrag;
import nl.banksim.domain.rekening.Iban;
import nl.banksim.domain.transactie.TransactieType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;

/**
 * S2.1 en S2.2: de transacties van een eigen rekening, per pagina van 50, zoals de repository ze aanlevert (nieuw naar
 * oud). De cursor naar de volgende pagina is ondertekend (ADR 0011).
 */
@WebMvcTest(TransactieController.class)
@Import({BeveiligingConfig.class, IngelogdeGebruiker.class, Cursor.class, TransactieControllerTests.Transacties.class})
@TestPropertySource(properties = "banksim.cursor.sleutel=" + TransactieControllerTests.SLEUTEL)
class TransactieControllerTests {

    static final String SLEUTEL = "test-sleutel-van-minstens-32-tekens";
    static final Iban VAN_JAN = Iban.simBank(1L);
    static final Iban OOK_VAN_JAN = Iban.simBank(2L);
    static final Positie LAATSTE_OP_PAGINA = new Positie(LocalDate.of(2026, 10, 1), 51L);

    /** Met welke positie en welk aantal de controller de repository aanriep. */
    static final List<String> AANROEPEN = new ArrayList<>();

    @Autowired
    MockMvc mvc;

    @MockitoBean
    JwtDecoder jwtDecoder;

    @Autowired
    Cursor cursor;

    @Test
    /** S4.1: ook type, tijdstip in Europe/Amsterdam, Van en Naar; zonder IBAN (betaalautomaat) is het iban null. */
    void geeftDeEerstePaginaVan50MetDatumEnBedragAlsStringEnZonderVolgendeAanHetEind() throws Exception {
        AANROEPEN.clear();
        mvc.perform(get("/api/rekeningen/" + OOK_VAN_JAN + "/transacties").with(gebruiker("jan-a1-1")))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        {"transacties": [
                           {"datum": "2026-10-02", "tegenpartij": "Werkgever B.V.", "bedrag": "3215.00",
                            "type": "ONLINE_BANKIEREN", "tijdstip": "2026-10-02T09:15:00+02:00",
                            "van": {"naam": "Werkgever B.V.", "iban": "NL20INGB0001234567"},
                            "naar": {"naam": "Jan", "iban": "NL94SIMB0000000001"}},
                           {"datum": "2026-10-01", "tegenpartij": "Albert Heijn", "bedrag": "-63.48",
                            "type": "BETAALAUTOMAAT", "tijdstip": "2026-10-01T13:26:00+02:00",
                            "van": {"naam": "Jan", "iban": "NL94SIMB0000000001"},
                            "naar": {"naam": "Albert Heijn", "iban": null}}],
                         "volgende": null}""",
                        JsonCompareMode.STRICT));
        assertThat(AANROEPEN).containsExactly(OOK_VAN_JAN + " tot 2026-10-05 vanaf het begin, 50");
    }

    @Test
    void deCursorUitVolgendeVraagtDePaginaNaDeLaatsteTransactie() throws Exception {
        String volgende = mvc.perform(get("/api/rekeningen/" + VAN_JAN + "/transacties").with(gebruiker("jan-a1-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.volgende").isString())
                .andReturn().getResponse().getContentAsString().replaceAll(".*\"volgende\":\"([^\"]+)\".*", "$1");
        AANROEPEN.clear();

        mvc.perform(get("/api/rekeningen/" + VAN_JAN + "/transacties").param("na", volgende).with(gebruiker("jan-a1-1")))
                .andExpect(status().isOk());

        assertThat(AANROEPEN).containsExactly(VAN_JAN + " tot 2026-10-05 na " + LAATSTE_OP_PAGINA + ", 50");
    }

    /** S5.1: de zoekvelden gaan als filter naar de repository, ook met "Toon meer". */
    @Test
    void geeftDeZoekveldenAlsFilterDoor() throws Exception {
        AANROEPEN.clear();
        mvc.perform(get("/api/rekeningen/" + OOK_VAN_JAN + "/transacties").with(gebruiker("jan-a1-1"))
                        .param("tekst", "albert").param("van", "27,95").param("tm", "100").param("type", "INCASSO")
                        .param("richting", "uitgaand"))
                .andExpect(status().isOk());

        assertThat(AANROEPEN).containsExactly(OOK_VAN_JAN + " tot 2026-10-05 vanaf het begin, 50, " + Zoekfilter.van(
                Optional.of("albert"), Optional.of("27,95"), Optional.of("100"), Optional.of("INCASSO"),
                Optional.of("uitgaand")));
    }

    /** RFC 9457: een ongeldige zoekopdracht is een 400 met een probleemtype en de melding voor de klant. */
    @Test
    void eenOngeldigBedragbereikGeeft400MetProbleemtype() throws Exception {
        AANROEPEN.clear();
        mvc.perform(get("/api/rekeningen/" + VAN_JAN + "/transacties").with(gebruiker("jan-a1-1"))
                        .param("van", "100").param("tm", "50"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("urn:banksim:probleem:ongeldig-bedragbereik"))
                .andExpect(jsonPath("$.detail").value("Bedrag van mag niet groter zijn dan Bedrag t/m"));
        assertThat(AANROEPEN).isEmpty();
    }

    @Test
    void eenOnleesbareCursorGeeft400() throws Exception {
        mvc.perform(get("/api/rekeningen/" + VAN_JAN + "/transacties").param("na", "geen-cursor").with(gebruiker("jan-a1-1")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void eenCursorVanEenAndereRekeningGeeft400() throws Exception {
        String vanDeAndereRekening = cursor.maak(OOK_VAN_JAN, LAATSTE_OP_PAGINA);
        mvc.perform(get("/api/rekeningen/" + VAN_JAN + "/transacties").param("na", vanDeAndereRekening)
                        .with(gebruiker("jan-a1-1")))
                .andExpect(status().isBadRequest());
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

        /** ADR 0013: de transacties tot en met "vandaag" volgens de SimulationClock, hier 5 oktober 2026. */
        @Bean
        SimulationClock simulationClock() {
            var simulatiedatum = new GeheugenSimulatiedatum();
            simulatiedatum.zet(LocalDate.of(2026, 10, 5));
            return new SimulationClock(Clock.fixed(Instant.parse("2026-10-22T12:00:00Z"), ZoneId.of("UTC")), simulatiedatum);
        }

        @Bean
        TransactieRepository transactieRepository() {
            // Op VAN_JAN volgt nog een pagina, op OOK_VAN_JAN niet.
            return (gebruikersnaam, iban, vandaag, filter, na, aantal) -> {
                AANROEPEN.add(iban + " tot " + vandaag + na.map(positie -> " na " + positie).orElse(" vanaf het begin")
                        + ", " + aantal + (filter.equals(Zoekfilter.GEEN) ? "" : ", " + filter));
                if (!"jan-a1-1".equals(gebruikersnaam) || !(VAN_JAN.equals(iban) || OOK_VAN_JAN.equals(iban))) {
                    return Optional.empty();
                }
                Partij jan = new Partij("Jan", Optional.of(VAN_JAN));
                return Optional.of(new Pagina(List.of(
                        new Transactie(LocalDate.of(2026, 10, 2), Bedrag.van("3215.00"), jan,
                                new Partij("Werkgever B.V.", Optional.of(Iban.van("NL20INGB0001234567"))),
                                TransactieType.ONLINE_BANKIEREN, OffsetDateTime.parse("2026-10-02T07:15:00Z")),
                        new Transactie(LocalDate.of(2026, 10, 1), Bedrag.van("-63.48"), jan,
                                new Partij("Albert Heijn", Optional.empty()),
                                TransactieType.BETAALAUTOMAAT, OffsetDateTime.parse("2026-10-01T11:26:00Z"))),
                        VAN_JAN.equals(iban) ? Optional.of(LAATSTE_OP_PAGINA) : Optional.empty()));
            };
        }
    }
}
