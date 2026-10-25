package nl.banksim.domain.transactie;

/** De 7 soorten transacties van een betaalrekening uit het FO (S4.1), plus Rente (S8.1) en Inleg (S9.1) op de spaarrekening; het scherm toont ze als "Online bankieren", "iDEAL | Wero" enzovoort. */
public enum TransactieType {
    ONLINE_BANKIEREN,
    IDEAL_WERO,
    BETAALAUTOMAAT,
    INCASSO,
    GELDAUTOMAAT,
    OVERSCHRIJVING,
    VERZAMELBETALING,
    /** S8.1: de maandelijkse rente op een spaarrekening (ADR 0028). */
    RENTE,
    /** S9.1: geld van de eigen betaalrekening naar de spaarrekening, aan de kant van de spaarrekening (ADR 0029). */
    INLEG
}
