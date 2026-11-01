package nl.banksim.api.betaling;

/** De probleemtypes van een geweigerde betaling of overschrijving (RFC 9457): {@code urn:banksim:probleem:<code>}. */
enum Weigering {

    ONBEKEND_CONTACT("onbekend-contact"),
    NAAM_PAST_NIET("naam-past-niet"),
    SALDO_ONTOEREIKEND("saldo-ontoereikend"),
    SLEUTEL_AL_GEBRUIKT("sleutel-al-gebruikt");

    private final String code;

    Weigering(String code) {
        this.code = code;
    }

    /** Het laatste deel van het probleemtype; de API belooft het, dus het staat hier letterlijk. */
    String code() {
        return code;
    }
}
