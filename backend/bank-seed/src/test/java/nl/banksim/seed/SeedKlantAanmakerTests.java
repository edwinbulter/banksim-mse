package nl.banksim.seed;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import nl.banksim.domain.geld.Bedrag;
import nl.banksim.domain.rekening.Iban;
import org.junit.jupiter.api.Test;

class SeedKlantAanmakerTests {

    private final List<String> logins = new ArrayList<>();
    private final List<String> rekeningen = new ArrayList<>();
    private final Logins keycloak = gebruikersnaam -> {
        logins.add(gebruikersnaam);
        return "wachtwoord-van-" + gebruikersnaam;
    };
    private final Rekeninghouders database = (gebruikersnaam, iban, openingssaldo) ->
            rekeningen.add(gebruikersnaam + " " + iban + " " + openingssaldo);
    private final Iban iban = Iban.simBank(123456789L);

    private final SeedKlantAanmaker aanmaker = new SeedKlantAanmaker(keycloak, database, () -> iban);

    @Test
    void maaktEenLoginMetDeAliasAlsGebruikersnaamEnEenBetaalrekeningMetOpeningssaldo() {
        GeseedeKlant klant = aanmaker.klantMetBetaalrekening("jan-f4de79-1", Bedrag.van("1842.17"));

        assertThat(logins).containsExactly("jan-f4de79-1");
        assertThat(rekeningen).containsExactly("jan-f4de79-1 " + iban + " 1842.17");
        assertThat(klant).isEqualTo(new GeseedeKlant("jan-f4de79-1", "wachtwoord-van-jan-f4de79-1", iban.toString()));
    }
}
