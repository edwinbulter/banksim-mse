package nl.banksim.domain.grootboek;

import java.math.BigDecimal;

import nl.banksim.domain.geld.Bedrag;

/** Nooit rood (FO, TO §6, ADR 0025): het laagste lopende saldo vanaf de uitvoerdatum moet de betaling dekken. */
public final class NooitRoodRegel {

    private NooitRoodRegel() {
    }

    public static boolean staatToe(SaldoVerloop verloop, Bedrag bedrag) {
        BigDecimal lopend = verloop.saldoOpUitvoerdatum().waarde();
        BigDecimal laagste = lopend;
        for (Bedrag beweging : verloop.latereBewegingen()) {
            lopend = lopend.add(beweging.waarde());
            laagste = laagste.min(lopend);
        }
        return laagste.compareTo(bedrag.waarde()) >= 0;
    }
}
