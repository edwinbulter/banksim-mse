package nl.banksim.domain.transactie;

/** De 7 soorten transacties uit het FO (S4.1); het scherm toont ze als "Online bankieren", "iDEAL | Wero" enzovoort. */
public enum TransactieType {
    ONLINE_BANKIEREN,
    IDEAL_WERO,
    BETAALAUTOMAAT,
    INCASSO,
    GELDAUTOMAAT,
    OVERSCHRIJVING,
    VERZAMELBETALING
}
