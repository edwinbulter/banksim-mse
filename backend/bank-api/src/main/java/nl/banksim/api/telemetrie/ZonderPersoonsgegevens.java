package nl.banksim.api.telemetrie;

import java.util.regex.Pattern;

import io.micrometer.common.KeyValue;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationFilter;
import org.springframework.stereotype.Component;

/**
 * De vangrail van ADR 0054: geen IBAN en geen zoekterm in de telemetrie. Een pad als /api/rekeningen/{iban} bevat een
 * IBAN, en een zoekopdracht een naam in de querystring. Het filter vervangt elk IBAN door {iban} en haalt de
 * querystring weg, in elk attribuut van een span en elk label van een metric. Een dubbel in bank-api en bank-bff: ze
 * delen geen module.
 */
@Component
class ZonderPersoonsgegevens implements ObservationFilter {

    private static final Pattern IBAN = Pattern.compile("[A-Z]{2}\\d{2}[A-Z]{4}\\d{10}");
    private static final Pattern QUERYSTRING = Pattern.compile("\\?.*$");

    @Override
    public Observation.Context map(Observation.Context context) {
        context.getHighCardinalityKeyValues().forEach(kv -> context.addHighCardinalityKeyValue(schoon(kv)));
        context.getLowCardinalityKeyValues().forEach(kv -> context.addLowCardinalityKeyValue(schoon(kv)));
        return context;
    }

    private static KeyValue schoon(KeyValue kv) {
        String waarde = QUERYSTRING.matcher(IBAN.matcher(kv.getValue()).replaceAll("{iban}")).replaceAll("");
        return KeyValue.of(kv.getKey(), waarde);
    }
}
