package nl.banksim.api.betaling;

/** Een betaling die de bank weigert, met het probleemtype voor de software en de melding voor de klant (RFC 9457). */
class BetalingGeweigerd extends RuntimeException {

    private final String probleem;

    BetalingGeweigerd(String probleem, String melding) {
        super(melding);
        this.probleem = probleem;
    }

    String probleem() {
        return probleem;
    }
}
