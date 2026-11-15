package nl.banksim.api.betaling;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.common.KeyValue;
import org.junit.jupiter.api.Test;
import org.springframework.http.server.observation.ServerRequestObservationContext;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * ADR 0054: een verzoek met een Idempotency-Key (ADR 0027) krijgt die sleutel als attribuut op zijn span. Zo is één
 * betaalpoging in Tempo terug te vinden. Een attribuut met veel varianten hoort op de span, niet op een metric.
 */
class SleutelOpDeSpanTests {

    private final SleutelOpDeSpan conventie = new SleutelOpDeSpan();

    @Test
    void zetDeSleutelOpDeSpan() {
        var verzoek = new MockHttpServletRequest("POST", "/api/betalingen");
        verzoek.addHeader("Idempotency-Key", "3f2a9c1e-7b4d-4e0a-9f1c-2d8e6b5a4c3f");

        assertThat(conventie.getHighCardinalityKeyValues(context(verzoek)))
                .contains(KeyValue.of("bank.idempotency_key", "3f2a9c1e-7b4d-4e0a-9f1c-2d8e6b5a4c3f"));
    }

    @Test
    void zonderSleutelGeenAttribuut() {
        var verzoek = new MockHttpServletRequest("GET", "/api/rekeningen");

        assertThat(conventie.getHighCardinalityKeyValues(context(verzoek)))
                .noneMatch(kv -> kv.getKey().equals("bank.idempotency_key"));
    }

    @Test
    void deSleutelKomtNietInDeMetrics() {
        var verzoek = new MockHttpServletRequest("POST", "/api/betalingen");
        verzoek.addHeader("Idempotency-Key", "3f2a9c1e-7b4d-4e0a-9f1c-2d8e6b5a4c3f");

        assertThat(conventie.getLowCardinalityKeyValues(context(verzoek)))
                .noneMatch(kv -> kv.getKey().equals("bank.idempotency_key"));
    }

    private static ServerRequestObservationContext context(MockHttpServletRequest verzoek) {
        return new ServerRequestObservationContext(verzoek, new MockHttpServletResponse());
    }
}
