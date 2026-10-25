package nl.banksim.domain.transactie;

/** De 7 soorten transacties van een betaalrekening uit het FO (S4.1), plus Rente op de spaarrekening (S8.1); het scherm toont ze als "Online bankieren", "iDEAL | Wero" enzovoort. */
public enum TransactieType {
    ONLINE_BANKIEREN,
    IDEAL_WERO,
    BETAALAUTOMAAT,
    INCASSO,
    GELDAUTOMAAT,
    OVERSCHRIJVING,
    VERZAMELBETALING,
    /** S8.1: de maandelijkse rente op een spaarrekening (ADR 0028). */
    RENTE
}
