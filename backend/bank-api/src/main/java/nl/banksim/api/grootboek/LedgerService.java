package nl.banksim.api.grootboek;

import nl.banksim.domain.grootboek.NooitRoodRegel;
import nl.banksim.domain.grootboek.Overboeking;
import nl.banksim.domain.rekening.Iban;
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
     * lopende saldo van de betalende rekening vanaf de uitvoerdatum moet de overboeking dekken. Eerst beide rekeningen
     * vergrendelen (ADR 0026), anders lezen gelijktijdige boekingen hetzelfde saldo. Een interne rekening van BankSim,
     * zoals de renterekening, mag negatief worden (ADR 0028).
     */
    @Transactional
    public void boek(Overboeking overboeking) {
        grootboek.vergrendel(overboeking.van().iban(), overboeking.naar().iban());
        Iban van = overboeking.van().iban();
        if (!grootboek.magRoodStaan(van)
                && !NooitRoodRegel.staatToe(grootboek.saldoVerloop(van, overboeking.datum()), overboeking.bedrag())) {
            throw new SaldoOntoereikend();
        }
        grootboek.schrijf(overboeking);
    }
}
