package nl.banksim.domain.grootboek;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import nl.banksim.domain.geld.Bedrag;
import nl.banksim.domain.transactie.TransactieType;

/**
 * Geld van de ene rekening van BankSim naar de andere (ADR 0024), op een tijdstip in Europe/Amsterdam. De datum van het
 * tijdstip is de uitvoerdatum. Omschrijving, betalingskenmerk en extra omschrijving zijn null als ze er niet zijn (FO:
 * optioneel).
 */
public record Overboeking(Partij van, Partij naar, Bedrag bedrag, TransactieType type, LocalDateTime tijdstip,
        String omschrijving, String betalingskenmerk, String extraOmschrijving, TransactieType typeVan,
        TransactieType typeNaar) {

    /** Beide kanten hebben het type van de overboeking, zoals bij een betaling of rente. */
    public Overboeking(Partij van, Partij naar, Bedrag bedrag, TransactieType type, LocalDateTime tijdstip,
            String omschrijving, String betalingskenmerk, String extraOmschrijving) {
        this(van, naar, bedrag, type, tijdstip, omschrijving, betalingskenmerk, extraOmschrijving, type, type);
    }

    /** Zonder omschrijving, betalingskenmerk en extra omschrijving; die zijn optioneel (FO). */
    public Overboeking(Partij van, Partij naar, Bedrag bedrag, TransactieType type, LocalDateTime tijdstip) {
        this(van, naar, bedrag, type, tijdstip, null, null, null);
    }

    /**
     * S9.1 (FO): een overschrijving tussen de eigen rekeningen. De kant van Van ziet een "Overschrijving", de kant van
     * Naar {@code typeNaar}, bijvoorbeeld een Inleg op de spaarrekening (ADR 0029).
     */
    public static Overboeking overschrijving(Partij van, Partij naar, Bedrag bedrag, TransactieType typeNaar,
            LocalDateTime tijdstip, String omschrijving) {
        return new Overboeking(van, naar, bedrag, TransactieType.OVERSCHRIJVING, tijdstip, omschrijving, null, null,
                TransactieType.OVERSCHRIJVING, typeNaar);
    }

    public Overboeking {
        if (bedrag.waarde().signum() <= 0) {
            throw new IllegalArgumentException("Een overboeking is groter dan 0: " + bedrag);
        }
        if (van.iban().equals(naar.iban())) {
            throw new IllegalArgumentException("Een overboeking gaat naar een andere rekening: " + van.iban());
        }
    }

    /** De afschrijving bij {@code van} en de bijschrijving bij {@code naar}, met elkaar als tegenpartij en elk met hun type. */
    public List<Boeking> boekingen() {
        return List.of(
                new Boeking(van.iban(), new Bedrag(bedrag.waarde().negate()), naar, typeVan),
                new Boeking(naar.iban(), bedrag, van, typeNaar));
    }

    public LocalDate datum() {
        return tijdstip.toLocalDate();
    }
}
