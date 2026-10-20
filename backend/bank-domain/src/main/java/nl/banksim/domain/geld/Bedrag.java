package nl.banksim.domain.geld;

import java.math.BigDecimal;
import java.util.regex.Pattern;

/**
 * Een geldbedrag in euro's, exact en met altijd 2 decimalen (ADR 0010). Als tekst met een punt als
 * decimaalteken, zoals in JSON: {@code "1842.17"}.
 */
public record Bedrag(BigDecimal waarde) {

    private static final Pattern NOTATIE = Pattern.compile("-?\\d+(\\.\\d{1,2})?");

    public Bedrag {
        if (waarde.scale() != 2) {
            throw new IllegalArgumentException("Een bedrag heeft precies 2 decimalen: " + waarde);
        }
    }

    /** Leest {@code "1842.17"}, {@code "10.5"} of {@code "10"}; meer dan 2 decimalen wordt geweigerd, niet afgerond. */
    public static Bedrag van(String tekst) {
        if (!NOTATIE.matcher(tekst).matches()) {
            throw new IllegalArgumentException("Geen geldig bedrag: " + tekst);
        }
        return new Bedrag(new BigDecimal(tekst).setScale(2));
    }

    @Override
    public String toString() {
        return waarde.toPlainString();
    }
}
