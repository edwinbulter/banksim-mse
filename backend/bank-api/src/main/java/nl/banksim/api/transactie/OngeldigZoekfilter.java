package nl.banksim.api.transactie;

/** Een zoekopdracht die niet kan; {@code probleem} is het probleemtype voor de API (RFC 9457), de melding is voor de klant. */
class OngeldigZoekfilter extends RuntimeException {

    private final String probleem;

    OngeldigZoekfilter(String probleem, String melding) {
        super(melding);
        this.probleem = probleem;
    }

    String probleem() {
        return probleem;
    }
}
