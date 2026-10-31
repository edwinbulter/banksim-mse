package nl.banksim.api.sql;

/** Injectie (slice 5): de invoer van een klant als letterlijke tekst in LIKE, met {@code ESCAPE '\'} in de query. */
public final class Like {

    private Like() {
    }

    /** "50%" → "50\%": %, _ en \ zijn gewone tekens, geen jokertekens of escape. */
    public static String letterlijk(String tekst) {
        return tekst.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
