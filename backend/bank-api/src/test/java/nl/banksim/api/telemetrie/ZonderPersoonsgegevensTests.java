package nl.banksim.api.telemetrie;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.common.KeyValue;
import io.micrometer.observation.Observation;
import org.junit.jupiter.api.Test;

/**
 * De vangrail van ADR 0054: geen IBAN en geen zoekterm in de telemetrie. Een pad als /api/rekeningen/{iban} bevat een
 * IBAN, en een zoekopdracht een naam in de querystring. Het filter haalt ze uit elk attribuut van een span.
 */
class ZonderPersoonsgegevensTests {

    private final ZonderPersoonsgegevens filter = new ZonderPersoonsgegevens();

    @Test
    void vervangtEenIbanInEenAttribuut() {
        var context = new Observation.Context();
        context.addHighCardinalityKeyValue(KeyValue.of("http.url", "/api/rekeningen/NL46SIMB0533315595/transacties"));

        assertThat(filter.map(context).getHighCardinalityKeyValue("http.url").getValue())
                .isEqualTo("/api/rekeningen/{iban}/transacties");
    }

    @Test
    void haaltDeQuerystringWeg() {
        var context = new Observation.Context();
        context.addHighCardinalityKeyValue(KeyValue.of("http.url", "http://bank-api:8080/api/contacten?q=Piet"));

        assertThat(filter.map(context).getHighCardinalityKeyValue("http.url").getValue())
                .isEqualTo("http://bank-api:8080/api/contacten");
    }

    @Test
    void ookInDeLabelsVanMetrics() {
        var context = new Observation.Context();
        context.addLowCardinalityKeyValue(KeyValue.of("uri", "/api/rekeningen/NL46SIMB0533315595"));

        assertThat(filter.map(context).getLowCardinalityKeyValue("uri").getValue()).isEqualTo("/api/rekeningen/{iban}");
    }

    @Test
    void laatAndereWaardenStaan() {
        var context = new Observation.Context();
        context.addHighCardinalityKeyValue(KeyValue.of("bank.idempotency_key", "3f2a9c1e-7b4d-4e0a-9f1c-2d8e6b5a4c3f"));

        assertThat(filter.map(context).getHighCardinalityKeyValue("bank.idempotency_key").getValue())
                .isEqualTo("3f2a9c1e-7b4d-4e0a-9f1c-2d8e6b5a4c3f");
    }
}
