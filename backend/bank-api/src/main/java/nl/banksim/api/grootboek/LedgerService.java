package nl.banksim.api.grootboek;

import nl.banksim.domain.geld.Bedrag;
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

    /**
     * Beide kanten in één databasetransactie; de trigger controleert ze bij commit. Nooit rood: het saldo van de
     * betalende rekening op de uitvoerdatum moet de overboeking dekken.
     */
    @Transactional
    public void boek(Overboeking overboeking) {
        Bedrag saldo = grootboek.saldo(overboeking.van().iban(), overboeking.datum());
        if (saldo.waarde().compareTo(overboeking.bedrag().waarde()) < 0) {
            throw new SaldoOntoereikend();
        }
        grootboek.schrijf(overboeking);
    }
}
