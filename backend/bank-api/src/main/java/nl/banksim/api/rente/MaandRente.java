package nl.banksim.api.rente;

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
            String omschrijving = "Rente " + maand.getMonth().getDisplayName(TextStyle.FULL, Locale.of("nl"));
            ledger.boek(new Overboeking(BANKSIM, new Partij(rekening.iban(), rekening.naam()), rente, TransactieType.RENTE,
                    maand.atEndOfMonth().atTime(23, 59), omschrijving, null, null));
        }
    }
}
