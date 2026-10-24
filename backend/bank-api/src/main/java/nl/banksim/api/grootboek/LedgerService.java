package nl.banksim.api.grootboek;

import nl.banksim.domain.grootboek.Overboeking;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** De enige plek waar geld wordt geboekt (ADR 0024). */
@Service
public class LedgerService {

    private final Grootboek grootboek;

    LedgerService(Grootboek grootboek) {
        this.grootboek = grootboek;
    }

    /** Beide kanten in één databasetransactie; de trigger controleert ze bij commit. */
    @Transactional
    public void boek(Overboeking overboeking) {
        grootboek.schrijf(overboeking);
    }
}
