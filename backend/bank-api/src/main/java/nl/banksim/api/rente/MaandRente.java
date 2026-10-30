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

    /** Bijgeschreven op de laatste dag van de maand, als "Rente januari"; zonder rente alleen vastgelegd. */
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
