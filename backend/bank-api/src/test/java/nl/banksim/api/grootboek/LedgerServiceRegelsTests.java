package nl.banksim.api.grootboek;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import nl.banksim.domain.geld.Bedrag;
import nl.banksim.domain.grootboek.Overboeking;
import nl.banksim.domain.grootboek.Partij;
import nl.banksim.domain.rekening.Iban;
import nl.banksim.domain.transactie.TransactieType;
import org.junit.jupiter.api.Test;

/**
 * ADR 0025, 0028: nooit rood geldt voor betaal- en spaarrekeningen, niet voor een interne rekening van BankSim. ADR 0030:
 * na het schrijven publiceert LedgerService {@link OverboekingGeboekt}, bij een weigering niet.
 */
class LedgerServiceRegelsTests {

    static final Partij BANKSIM = new Partij(Iban.simBank(0L), "BankSim");
    static final Partij SPAAR = new Partij(Iban.simBank(2L), "Jan");

    private final GeheugenGrootboek grootboek = new GeheugenGrootboek();
    private final List<Object> gebeurtenissen = new ArrayList<>();
    private final LedgerService ledger = new LedgerService(grootboek, gebeurtenissen::add);

    @Test
    void eenInterneRekeningMagNegatiefWorden() {
        grootboek.rekening("banksim", BANKSIM.iban(), "BankSim", Bedrag.van("0.00"));
        grootboek.mogenRoodStaan(BANKSIM.iban());

        ledger.boek(rente());

        assertThat(grootboek.geschreven()).hasSize(1);
    }

    @Test
    void eenGewoneRekeningMagDatNiet() {
        grootboek.rekening("banksim", BANKSIM.iban(), "BankSim", Bedrag.van("0.00"));

        assertThatThrownBy(() -> ledger.boek(rente())).isInstanceOf(SaldoOntoereikend.class);
        assertThat(grootboek.geschreven()).isEmpty();
    }

    @Test
    void naHetSchrijvenVolgtOverboekingGeboekt() {
        grootboek.rekening("banksim", BANKSIM.iban(), "BankSim", Bedrag.van("0.00"));
        grootboek.mogenRoodStaan(BANKSIM.iban());

        ledger.boek(rente());

        assertThat(gebeurtenissen).containsExactly(new OverboekingGeboekt(rente()));
    }

    @Test
    void naEenWeigeringVolgtGeenGebeurtenis() {
        grootboek.rekening("banksim", BANKSIM.iban(), "BankSim", Bedrag.van("0.00"));

        assertThatThrownBy(() -> ledger.boek(rente())).isInstanceOf(SaldoOntoereikend.class);
        assertThat(gebeurtenissen).isEmpty();
    }

    private static Overboeking rente() {
        return new Overboeking(BANKSIM, SPAAR, Bedrag.van("25.48"), TransactieType.RENTE, LocalDateTime.of(2026, 1, 31, 23, 59));
    }
}
