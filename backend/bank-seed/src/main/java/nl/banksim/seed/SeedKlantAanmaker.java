package nl.banksim.seed;

import java.util.function.Supplier;

import nl.banksim.domain.geld.Bedrag;
import nl.banksim.domain.rekening.Iban;

/** Een klant is een login met dezelfde gebruikersnaam als de rekeninghouder; de alias is die gebruikersnaam. */
class SeedKlantAanmaker implements KlantAanmaker {

    private final Logins logins;
    private final Rekeninghouders rekeninghouders;
    private final Supplier<Iban> nieuweIban;

    SeedKlantAanmaker(Logins logins, Rekeninghouders rekeninghouders, Supplier<Iban> nieuweIban) {
        this.logins = logins;
        this.rekeninghouders = rekeninghouders;
        this.nieuweIban = nieuweIban;
    }

    @Override
    public GeseedeKlant klantMetBetaalrekening(String alias, Bedrag saldo) {
        Iban iban = nieuweIban.get();
        rekeninghouders.voegToeMetBetaalrekening(alias, iban, saldo);
        String wachtwoord = logins.maakLogin(alias);
        return new GeseedeKlant(alias, wachtwoord, iban.toString());
    }
}
