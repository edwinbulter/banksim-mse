package nl.banksim.api.betaling;

/** Een betaling die de bank weigert, met het probleemtype voor de software en de melding voor de klant (RFC 9457). */
class BetalingGeweigerd extends RuntimeException {

    private final Weigering probleem;

    BetalingGeweigerd(Weigering probleem, String melding) {
        super(melding);
        this.probleem = probleem;
    }

    Weigering probleem() {
        return probleem;
    }
}
