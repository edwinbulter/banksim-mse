package nl.banksim.seed;

import java.util.Optional;
import java.util.function.Supplier;

import nl.banksim.domain.geld.Bedrag;
import nl.banksim.domain.rekening.Iban;
import org.springframework.transaction.support.TransactionOperations;

/**
 * ADR 0040, S11.1: de 10 klanten en de beheerder van de demo; een tweede keer verandert er niets. Een klant is in één
 * databasetransactie de rekeninghouder, zijn twee rekeningen, zijn transacties (ADR 0041) en de login: mislukt de login,
 * dan volgt alles bij de volgende installatie opnieuw.
 */
class DemoData {

    private static final Bedrag LEEG = Bedrag.van("0.00");

    private final DemoLogins logins;
    private final BestaandeKlanten bestaand;
    private final Rekeninghouders rekeninghouders;
    private final Spaarrekeningen spaarrekeningen;
    private final Transacties transacties;
    private final Supplier<Iban> nieuweIban;
    private final TransactionOperations transactie;
    private final String klantWachtwoord;
    private final String beheerderWachtwoord;

    DemoData(DemoLogins logins, BestaandeKlanten bestaand, Rekeninghouders rekeninghouders, Spaarrekeningen spaarrekeningen,
            Transacties transacties, Supplier<Iban> nieuweIban, TransactionOperations transactie, String klantWachtwoord, String beheerderWachtwoord) {
        this.logins = logins;
        this.bestaand = bestaand;
        this.rekeninghouders = rekeninghouders;
        this.spaarrekeningen = spaarrekeningen;
        this.transacties = transacties;
        this.nieuweIban = nieuweIban;
        this.transactie = transactie;
        this.klantWachtwoord = klantWachtwoord;
        this.beheerderWachtwoord = beheerderWachtwoord;
    }

    void maak() {
        if (leeg(klantWachtwoord) || leeg(beheerderWachtwoord)) {
            throw new IllegalStateException("De wachtwoorden uit het Secret bank-demo ontbreken");
        }
        for (DemoKlanten.DemoKlant klant : DemoKlanten.ALLE) {
            if (!bestaand.bestaat(klant.gebruikersnaam())) {
                transactie.executeWithoutResult(status -> maak(klant));
            }
        }
        logins.zorgVoorBeheerder(DemoKlanten.BEHEERDER, beheerderWachtwoord);
    }

    /** Zonder datum van openen: de maandafsluiting slaat de spaarrekening over, tot er transacties zijn (ADR 0040). */
    private void maak(DemoKlanten.DemoKlant klant) {
        Iban betaalrekening = nieuweIban.get();
        rekeninghouders.voegToeMetBetaalrekening(klant.gebruikersnaam(), klant.naam(), betaalrekening, klant.beginsaldo());
        spaarrekeningen.open(klant.gebruikersnaam(), LEEG, Optional.empty());
        transacties.boekAlle(betaalrekening, Generator.boekingen(klant));
        logins.zorgVoorKlant(klant.gebruikersnaam(), klantWachtwoord);
    }

    private static boolean leeg(String wachtwoord) {
        return wachtwoord == null || wachtwoord.isBlank();
    }
}
