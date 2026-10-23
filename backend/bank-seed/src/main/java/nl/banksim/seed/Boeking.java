package nl.banksim.seed;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import nl.banksim.domain.geld.Bedrag;
import nl.banksim.domain.rekening.Iban;
import nl.banksim.domain.transactie.TransactieType;

/**
 * Eén transactie om te boeken (S4.1). Het tijdstip is de klok in Europe/Amsterdam; de datum is die dag. Zonder
 * tegen-IBAN (betaalautomaat) toont Naar geen IBAN.
 */
public record Boeking(LocalDateTime tijdstip, Bedrag bedrag, String tegenpartij, TransactieType type,
        Optional<Iban> tegenIban) {

    /** Alleen een datum: Online bankieren om middernacht, zonder tegen-IBAN. */
    static Boeking opDatum(LocalDate datum, Bedrag bedrag, String tegenpartij) {
        return new Boeking(datum.atStartOfDay(), bedrag, tegenpartij, TransactieType.ONLINE_BANKIEREN, Optional.empty());
    }

    LocalDate datum() {
        return tijdstip.toLocalDate();
    }
}
