package nl.banksim.api.rekening;

/** Soort rekening, zoals in de database en in JSON. */
enum Soort {
    BETAAL("betaal"),
    SPAAR("spaar");

    private final String code;

    Soort(String code) {
        this.code = code;
    }

    String code() {
        return code;
    }

    static Soort van(String code) {
        for (Soort soort : values()) {
            if (soort.code.equals(code)) {
                return soort;
            }
        }
        throw new IllegalArgumentException("Onbekende soort rekening: " + code);
    }
}
