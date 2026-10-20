package nl.banksim.domain.rekening;

import java.math.BigInteger;
import java.util.regex.Pattern;

/** Een Nederlandse IBAN, zonder spaties, met een geldig mod-97-controlegetal. */
public record Iban(String waarde) {

    private static final Pattern NEDERLANDS = Pattern.compile("NL\\d{2}[A-Z]{4}\\d{10}");
    private static final BigInteger NEGENENNEGENTIG = BigInteger.valueOf(97);
    private static final long MAX_REKENINGNUMMER = 9_999_999_999L;

    public Iban {
        if (!NEDERLANDS.matcher(waarde).matches() || rest(waarde) != 1) {
            throw new IllegalArgumentException("Geen geldige Nederlandse IBAN: " + waarde);
        }
    }

    public static Iban van(String tekst) {
        return new Iban(tekst);
    }

    /** De IBAN van rekeningnummer {@code rekeningnummer} bij de gesimuleerde bank (bankcode SIMB). */
    public static Iban simBank(long rekeningnummer) {
        if (rekeningnummer < 0 || rekeningnummer > MAX_REKENINGNUMMER) {
            throw new IllegalArgumentException("Rekeningnummer heeft meer dan 10 cijfers: " + rekeningnummer);
        }
        String bban = "SIMB%010d".formatted(rekeningnummer);
        int controlegetal = 98 - rest("NL00" + bban);
        return new Iban("NL%02d%s".formatted(controlegetal, bban));
    }

    /** ISO 13616: landcode en controlegetal naar achteren, letters als getallen (A = 10), dan modulo 97. */
    private static int rest(String iban) {
        String herschikt = iban.substring(4) + iban.substring(0, 4);
        StringBuilder cijfers = new StringBuilder();
        herschikt.chars().forEach(c -> cijfers.append(Character.getNumericValue(c)));
        return new BigInteger(cijfers.toString()).mod(NEGENENNEGENTIG).intValue();
    }

    @Override
    public String toString() {
        return waarde;
    }
}
