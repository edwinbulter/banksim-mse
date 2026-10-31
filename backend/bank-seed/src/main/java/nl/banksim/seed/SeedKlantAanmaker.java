package nl.banksim.seed;

import java.util.function.Supplier;

import nl.banksim.domain.geld.Bedrag;
import nl.banksim.domain.rekening.Iban;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.support.TransactionOperations;

/** Een klant is een login met dezelfde gebruikersnaam als de rekeninghouder; de alias is die gebruikersnaam. */
class SeedKlantAanmaker implements KlantAanmaker {

    private final Logins logins;
    private final Rekeninghouders rekeninghouders;
    private final Supplier<Iban> nieuweIban;
    private final TransactionOperations transactie;

    SeedKlantAanmaker(Logins logins, Rekeninghouders rekeninghouders, Supplier<Iban> nieuweIban,
            TransactionOperations transactie) {
        this.logins = logins;
        this.rekeninghouders = rekeninghouders;
        this.nieuweIban = nieuweIban;
        this.transactie = transactie;
    }

    /**
     * Backlog fase 4.1: de rekeninghouder en de login in één databasetransactie. Mislukt de login in Keycloak, dan wordt
     * de rekeninghouder teruggedraaid. Bestaat de klant al, dan stopt de opdracht vóór Keycloak, met een duidelijke fout.
     */
    @Override
    public GeseedeKlant klantMetBetaalrekening(String alias, String naam, Bedrag saldo) {
        Iban iban = nieuweIban.get();
        try {
            return transactie.execute(status -> {
                rekeninghouders.voegToeMetBetaalrekening(alias, naam, iban, saldo);
                String wachtwoord = logins.maakLogin(alias);
                return new GeseedeKlant(alias, wachtwoord, iban.toString());
            });
        } catch (DuplicateKeyException e) {
            throw new IllegalArgumentException("Klant " + alias + " bestaat al", e);
        }
    }
}
