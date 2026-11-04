package nl.banksim.seed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import nl.banksim.domain.grootboek.Overboeking;
import nl.banksim.domain.rekening.Iban;
import nl.banksim.domain.transactie.TransactieType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.transaction.support.TransactionOperations;

/** ADR 0040, S11.1. */
class DemoDataTests {

    private final List<String> gedaan = new ArrayList<>();
    private final Set<String> bestaand = new HashSet<>();
    private final AtomicLong laatsteIban = new AtomicLong();
    private final DemoLogins logins = new DemoLogins() {
        @Override
        public void zorgVoorKlant(String gebruikersnaam, String wachtwoord) {
            gedaan.add("login " + gebruikersnaam + " " + wachtwoord);
        }

        @Override
        public void zorgVoorBeheerder(String gebruikersnaam, String wachtwoord) {
            gedaan.add("beheerder " + gebruikersnaam + " " + wachtwoord);
        }
    };
    private final Rekeninghouders rekeninghouders = (gebruikersnaam, naam, iban, saldo) ->
            gedaan.add("betaal " + gebruikersnaam + " " + naam + " " + iban + " " + saldo);
    private final Spaarrekeningen spaarrekeningen = (gebruikersnaam, saldo, geopendOp) -> {
        gedaan.add("spaar " + gebruikersnaam + " " + saldo + " " + geopendOp.isPresent());
        return Iban.simBank(99L);
    };

    private final Transacties transacties = new Transacties() {
        @Override
        public void boek(Iban iban, Boeking boeking) {
            throw new AssertionError("De demo-data boekt alles van een klant in een keer");
        }

        @Override
        public void boekAlle(Iban iban, List<Boeking> boekingen) {
            gedaan.add("boekingen " + iban + " " + boekingen.size());
        }
    };

    private final List<Overboeking> geschreven = new ArrayList<>();

    private final Overboekingen overboekingen = lijst -> {
        geschreven.addAll(lijst);
        if (!lijst.isEmpty()) {
            Overboeking eerste = lijst.getFirst();
            gedaan.add("overboekingen " + eerste.van().iban() + " " + eerste.naar().iban() + " " + lijst.size());
        }
    };

    private final Rentebijschrijvingen rentebijschrijvingen = (spaarrekening, maanden) ->
            gedaan.add("rentemaanden " + spaarrekening + " " + maanden.size());

    private DemoData demo(String klantWachtwoord, String beheerderWachtwoord) {
        return new DemoData(logins, bestaand::contains, rekeninghouders, spaarrekeningen, transacties, overboekingen, rentebijschrijvingen,
                () -> Iban.simBank(laatsteIban.incrementAndGet()), TransactionOperations.withoutTransaction(),
                klantWachtwoord, beheerderWachtwoord);
    }

    @Test
    void maaktTienKlantenMetEenBetaalEnSpaarrekeningEnLoginEnDaarnaDeBeheerder() {
        demo("klant-ww", "beheerder-ww").maak();

        assertThat(gedaan).hasSize(61);
        assertThat(gedaan.subList(0, 6)).containsExactly(
                "betaal jdevries Jan de Vries " + Iban.simBank(1L) + " 1800.00",
                "spaar jdevries 6500.00 true",
                "boekingen " + Iban.simBank(1L) + " " + Generator.boekingen(DemoKlanten.ALLE.getFirst()).size(),
                "overboekingen " + Iban.simBank(1L) + " " + Iban.simBank(99L) + " 126",
                "rentemaanden " + Iban.simBank(99L) + " 63",
                "login jdevries klant-ww");
        assertThat(gedaan).contains("betaal melamrani Mohamed El Amrani " + Iban.simBank(3L) + " 1500.00",
                "betaal nhendriks Noor Hendriks " + Iban.simBank(10L) + " 3500.00", "login nhendriks klant-ww");
        assertThat(gedaan.getLast()).isEqualTo("beheerder beheerder beheerder-ww");
    }

    /** ADR 0042: alleen Lisa en Mohamed nemen op, van de spaarrekening naar hun eigen betaalrekening. */
    @Test
    void schrijftDeOpnamesVanDeSpaarrekeningNaarDeBetaalrekening() {
        demo("klant-ww", "beheerder-ww").maak();

        assertThat(geschreven).filteredOn(overboeking -> overboeking.van().iban().equals(Iban.simBank(99L)))
                .extracting(overboeking -> overboeking.naar().iban())
                .containsExactly(Iban.simBank(3L), Iban.simBank(3L), Iban.simBank(4L));
    }

    /** ADR 0043: de rente komt van de renterekening van BankSim, met type Rente, en gaat naar de spaarrekening. */
    @Test
    void schrijftDeRenteVanBankSimNaarDeSpaarrekening() {
        demo("klant-ww", "beheerder-ww").maak();

        assertThat(geschreven).filteredOn(overboeking -> overboeking.van().naam().equals("BankSim"))
                .hasSize(10 * 63)
                .allSatisfy(overboeking -> {
                    assertThat(overboeking.van().iban()).isEqualTo(Iban.van("NL24SIMB0000000000"));
                    assertThat(overboeking.naar().iban()).isEqualTo(Iban.simBank(99L));
                    assertThat(overboeking.type()).isEqualTo(TransactieType.RENTE);
                    assertThat(overboeking.omschrijving()).startsWith("Rente ");
                });
    }

    @Test
    void eenTweedeKeerRaaktGeenEnkeleKlantMaarControleertWelDeBeheerder() {
        DemoKlanten.ALLE.forEach(klant -> bestaand.add(klant.gebruikersnaam()));

        demo("klant-ww", "beheerder-ww").maak();

        assertThat(gedaan).containsExactly("beheerder beheerder beheerder-ww");
    }

    @Test
    void eenAfgebrokenEerstePogingWordtAangevuldZonderWatErAlIs() {
        bestaand.add("jdevries");

        demo("klant-ww", "beheerder-ww").maak();

        assertThat(gedaan).hasSize(55).noneMatch(stap -> stap.contains("jdevries"));
    }

    /** Zonder wachtwoord uit het Secret bank-demo wordt er niets half aangemaakt. */
    @ParameterizedTest
    @CsvSource(nullValues = "null", value = {"null,b", "'',b", "'  ',b", "k,null", "k,''", "k,'  '"})
    void zonderWachtwoordWordtErNietsAangemaakt(String klantWachtwoord, String beheerderWachtwoord) {
        assertThatThrownBy(() -> demo(klantWachtwoord, beheerderWachtwoord).maak())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("bank-demo");
        assertThat(gedaan).isEmpty();
    }
}
