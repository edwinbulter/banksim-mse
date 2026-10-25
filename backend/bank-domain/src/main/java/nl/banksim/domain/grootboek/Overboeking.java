package nl.banksim.domain.grootboek;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import nl.banksim.domain.geld.Bedrag;
import nl.banksim.domain.transactie.TransactieType;

/**
 * Geld van de ene rekening van BankSim naar de andere (ADR 0024), op een tijdstip in Europe/Amsterdam. De datum van het
 * tijdstip is de uitvoerdatum. Omschrijving en betalingskenmerk zijn null als ze er niet zijn (FO: optioneel).
 */
public record Overboeking(Partij van, Partij naar, Bedrag bedrag, TransactieType type, LocalDateTime tijdstip,
        String omschrijving, String betalingskenmerk) {

    /** Zonder omschrijving en betalingskenmerk; die zijn optioneel (FO). */
    public Overboeking(Partij van, Partij naar, Bedrag bedrag, TransactieType type, LocalDateTime tijdstip) {
        this(van, naar, bedrag, type, tijdstip, null, null);
    }

    public Overboeking {
        if (bedrag.waarde().signum() <= 0) {
            throw new IllegalArgumentException("Een overboeking is groter dan 0: " + bedrag);
        }
        if (van.iban().equals(naar.iban())) {
            throw new IllegalArgumentException("Een overboeking gaat naar een andere rekening: " + van.iban());
        }
    }

    /** De afschrijving bij {@code van} en de bijschrijving bij {@code naar}, met elkaar als tegenpartij. */
    public List<Boeking> boekingen() {
        return List.of(
                new Boeking(van.iban(), new Bedrag(bedrag.waarde().negate()), naar),
                new Boeking(naar.iban(), bedrag, van));
    }

    public LocalDate datum() {
        return tijdstip.toLocalDate();
    }
}
