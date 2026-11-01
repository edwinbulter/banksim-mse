package nl.banksim.api.transactie;

/** Een zoekopdracht die niet kan; {@code probleem} is het probleemtype voor de API (RFC 9457), de melding is voor de klant. */
class OngeldigZoekfilter extends RuntimeException {

    private final ZoekProbleem probleem;

    OngeldigZoekfilter(ZoekProbleem probleem, String melding) {
        super(melding);
        this.probleem = probleem;
    }

    ZoekProbleem probleem() {
        return probleem;
    }
}
