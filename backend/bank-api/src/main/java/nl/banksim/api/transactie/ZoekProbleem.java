package nl.banksim.api.transactie;

/** De probleemtypes van een ongeldige zoekopdracht (RFC 9457): {@code urn:banksim:probleem:<code>}. */
enum ZoekProbleem {

    ONGELDIG_BEDRAG("ongeldig-bedrag"),
    ONGELDIG_BEDRAGBEREIK("ongeldig-bedragbereik"),
    ONGELDIG_TYPE("ongeldig-type"),
    ONGELDIGE_RICHTING("ongeldige-richting");

    private final String code;

    ZoekProbleem(String code) {
        this.code = code;
    }

    /** Het laatste deel van het probleemtype; de API belooft het, dus het staat hier letterlijk. */
    String code() {
        return code;
    }
}
