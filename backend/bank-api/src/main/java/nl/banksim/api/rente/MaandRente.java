package nl.banksim.api.rente;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.Locale;

import nl.banksim.api.grootboek.LedgerService;
import nl.banksim.domain.geld.Bedrag;
import nl.banksim.domain.grootboek.Overboeking;
import nl.banksim.domain.grootboek.Partij;
import nl.banksim.domain.rekening.Iban;
import nl.banksim.domain.rente.RenteCalculator;
import nl.banksim.domain.transactie.TransactieType;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** De rente van één spaarrekening over één maand: vastleggen en boeken in één databasetransactie (ADR 0028). */
@Component
class MaandRente {

    /** De renterekening van BankSim (V11); de spaarrekening ziet "BankSim" als tegenpartij (FO). */
    static final Partij BANKSIM = new Partij(Iban.van("NL24SIMB0000000000"), "BankSim");

    private final RenteOpslag opslag;
    private final LedgerService ledger;

    MaandRente(RenteOpslag opslag, LedgerService ledger) {
        this.opslag = opslag;
        this.ledger = ledger;
    }

    /**
     * Schrijft de rente bij op de laatste dag van de maand, als "Rente januari". Zonder rente legt hij de maand alleen vast.
     *
     * <p>Het vastleggen moet de eerste stap blijven (ADR 0039). Die INSERT vraagt via de foreign key een lock op de rij
     * van de spaarrekening. Een opname of inleg en de afsluiting wachten daardoor op elkaar:
     * <ul>
     * <li>Is de opname of inleg al bezig, dan wacht de afsluiting hier, en telt de rente de boeking daarna mee.
     * <li>Komt de opname of inleg later, dan wacht die tot de afsluiting klaar is, en boekt daarna een rentecorrectie.
     * </ul>
     */
    @Transactional
    public void boek(Spaarrekening rekening, YearMonth maand) {
        if (!opslag.markeer(rekening.iban(), maand)) {
            return;
        }
        Bedrag rente = RenteCalculator.rente(
                opslag.dagsaldi(rekening.iban(), rekening.eersteRentedag(maand), maand.atEndOfMonth()));
        if (rente.waarde().signum() > 0) {
            ledger.boek(new Overboeking(BANKSIM, new Partij(rekening.iban(), rekening.naam()), rente, TransactieType.RENTE,
                    maand.atEndOfMonth().atTime(23, 59), "Rente " + naam(maand), null, null));
        }
    }

    /**
     * ADR 0030: de rente van een bijgeschreven maand opnieuw uitrekenen, en het verschil met wat er al geboekt is als
     * "Rentecorrectie februari" boeken op de laatste dag van de maand. Minder rente gaat van de spaarrekening terug naar
     * BankSim. Bestaande boekingen blijven staan.
     */
    public void corrigeer(Spaarrekening rekening, YearMonth maand) {
        BigDecimal opnieuw = RenteCalculator.rente(
                opslag.dagsaldi(rekening.iban(), rekening.eersteRentedag(maand), maand.atEndOfMonth())).waarde();
        BigDecimal verschil = opnieuw.subtract(opslag.geboekteRente(rekening.iban(), maand).waarde());
        if (verschil.signum() == 0) {
            return;
        }
        Partij spaar = new Partij(rekening.iban(), rekening.naam());
        ledger.boek(new Overboeking(verschil.signum() > 0 ? BANKSIM : spaar, verschil.signum() > 0 ? spaar : BANKSIM,
                new Bedrag(verschil.abs()), TransactieType.RENTE, maand.atEndOfMonth().atTime(23, 59),
                "Rentecorrectie " + naam(maand), null, null));
    }

    private static String naam(YearMonth maand) {
        return maand.getMonth().getDisplayName(TextStyle.FULL, Locale.of("nl"));
    }
}
