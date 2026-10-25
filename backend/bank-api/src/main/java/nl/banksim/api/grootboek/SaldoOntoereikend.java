package nl.banksim.api.grootboek;

/** Nooit rood (ADR 0025): de boeking zou het saldo onder € 0,00 brengen, op de uitvoerdatum of later. */
public class SaldoOntoereikend extends RuntimeException {

    SaldoOntoereikend() {
        super("Saldo ontoereikend: deze betaling zou het saldo onder € 0,00 brengen, nu of op een latere datum.");
    }
}
