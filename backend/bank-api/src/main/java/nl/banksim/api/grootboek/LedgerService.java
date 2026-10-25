package nl.banksim.api.grootboek;

import nl.banksim.domain.grootboek.NooitRoodRegel;
import nl.banksim.domain.grootboek.Overboeking;
import nl.banksim.domain.grootboek.SaldoVerloop;
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
     * Beide kanten in één databasetransactie; de trigger controleert ze bij commit. Nooit rood (ADR 0025): het laagste
     * lopende saldo van de betalende rekening vanaf de uitvoerdatum moet de overboeking dekken.
     */
    @Transactional
    public void boek(Overboeking overboeking) {
        SaldoVerloop verloop = grootboek.saldoVerloop(overboeking.van().iban(), overboeking.datum());
        if (!NooitRoodRegel.staatToe(verloop, overboeking.bedrag())) {
            throw new SaldoOntoereikend();
        }
        grootboek.schrijf(overboeking);
    }
}
