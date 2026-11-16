package nl.banksim.seed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import nl.banksim.domain.rekening.Iban;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionOperations;

/** ADR 0055: de kring van verkeersklanten voor k6. */
class VerkeersklantenTests {

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
            throw new AssertionError("De kring heeft geen beheerder");
        }
    };
    private final Rekeninghouders rekeninghouders = (gebruikersnaam, naam, iban, saldo) ->
            gedaan.add("betaal " + gebruikersnaam + " " + naam + " " + iban + " " + saldo);
    private final Contacten contacten = (iban, naam) -> gedaan.add("contact " + naam + " " + iban);

    private Verkeersklanten verkeer(String wachtwoord) {
        return new Verkeersklanten(logins, bestaand::contains, rekeninghouders, contacten,
                () -> Iban.simBank(laatsteIban.incrementAndGet()), TransactionOperations.withoutTransaction(), wachtwoord);
    }

    /** Elk een betaalrekening met € 1.000,00, als contact voor de anderen, en een login: zo kan k6 in de kring betalen. */
    @Test
    void maaktVijfKlantenDieElkaarKunnenBetalen() {
        verkeer("verkeer-ww").maak();

        assertThat(gedaan).hasSize(15);
        assertThat(gedaan.subList(0, 3)).containsExactly(
                "betaal verkeer-1 Verkeer 1 " + Iban.simBank(1L) + " 1000.00",
                "contact Verkeer 1 " + Iban.simBank(1L),
                "login verkeer-1 verkeer-ww");
        assertThat(gedaan).contains("betaal verkeer-5 Verkeer 5 " + Iban.simBank(5L) + " 1000.00");
    }

    /** Idempotent, zoals de demo-data (ADR 0040): bestaat de kring al, dan verandert er niets. */
    @Test
    void eenTweedeKeerVerandertErNiets() {
        bestaand.add("verkeer-1");

        verkeer("verkeer-ww").maak();

        assertThat(gedaan).isEmpty();
    }

    @Test
    void zonderWachtwoordUitHetSecretFaaltHetMetEenMelding() {
        assertThatThrownBy(() -> verkeer("").maak()).hasMessageContaining("bank-verkeer");
    }
}
