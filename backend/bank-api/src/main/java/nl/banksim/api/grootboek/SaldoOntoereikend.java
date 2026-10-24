package nl.banksim.api.grootboek;

/** Nooit rood: de boeking zou het saldo onder € 0,00 brengen. */
public class SaldoOntoereikend extends RuntimeException {

    SaldoOntoereikend() {
        super("Saldo ontoereikend: deze betaling zou het saldo onder € 0,00 brengen.");
    }
}
