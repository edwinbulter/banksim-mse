package nl.banksim.api.betaling;

import io.micrometer.common.KeyValues;
import org.springframework.http.server.observation.DefaultServerRequestObservationConvention;
import org.springframework.http.server.observation.ServerRequestObservationContext;
import org.springframework.stereotype.Component;

/**
 * ADR 0054: de Idempotency-Key van een betaling of overschrijving (ADR 0027) komt als attribuut op de span van het
 * verzoek, zodat één betaalpoging in Tempo te vinden is. Alleen op de span: elke sleutel is anders, en als label van een
 * metric zou hij Prometheus vullen.
 */
@Component
class SleutelOpDeSpan extends DefaultServerRequestObservationConvention {

    @Override
    public KeyValues getHighCardinalityKeyValues(ServerRequestObservationContext context) {
        String sleutel = context.getCarrier().getHeader("Idempotency-Key");
        KeyValues standaard = super.getHighCardinalityKeyValues(context);
        return sleutel == null ? standaard : standaard.and("bank.idempotency_key", sleutel);
    }
}
