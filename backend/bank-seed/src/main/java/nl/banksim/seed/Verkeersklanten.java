package nl.banksim.seed;

import java.util.List;
import java.util.function.Supplier;
import java.util.stream.IntStream;

import nl.banksim.domain.geld.Bedrag;
import nl.banksim.domain.rekening.Iban;
import org.springframework.transaction.support.TransactionOperations;

/**
 * ADR 0055: de kring van verkeersklanten voor k6. Elk heeft een betaalrekening en staat als contact bij de anderen, zodat
 * k6 binnen de kring betaalt en het geld niet opraakt. Een tweede keer verandert er niets, zoals bij de demo-data
 * (ADR 0040). De klanten ontstaan samen in één databasetransactie.
 */
class Verkeersklanten {

    static final List<String> GEBRUIKERSNAMEN = IntStream.rangeClosed(1, 5).mapToObj(nummer -> "verkeer-" + nummer).toList();
    private static final Bedrag BEGINSALDO = Bedrag.van("1000.00");

    private final DemoLogins logins;
    private final BestaandeKlanten bestaand;
    private final Rekeninghouders rekeninghouders;
    private final Contacten contacten;
    private final Supplier<Iban> nieuweIban;
    private final TransactionOperations transactie;
    private final String wachtwoord;

    Verkeersklanten(DemoLogins logins, BestaandeKlanten bestaand, Rekeninghouders rekeninghouders, Contacten contacten,
            Supplier<Iban> nieuweIban, TransactionOperations transactie, String wachtwoord) {
        this.logins = logins;
        this.bestaand = bestaand;
        this.rekeninghouders = rekeninghouders;
        this.contacten = contacten;
        this.nieuweIban = nieuweIban;
        this.transactie = transactie;
        this.wachtwoord = wachtwoord;
    }

    void maak() {
        if (wachtwoord == null || wachtwoord.isBlank()) {
            throw new IllegalStateException("Het wachtwoord uit het Secret bank-verkeer ontbreekt");
        }
        if (GEBRUIKERSNAMEN.stream().noneMatch(bestaand::bestaat)) {
            transactie.executeWithoutResult(status -> GEBRUIKERSNAMEN.forEach(this::maak));
        }
    }

    /** "verkeer-1" heet "Verkeer 1": een naam die bij geen echte klant hoort. */
    private void maak(String gebruikersnaam) {
        String naam = "Verkeer " + gebruikersnaam.substring("verkeer-".length());
        Iban betaalrekening = nieuweIban.get();
        rekeninghouders.voegToeMetBetaalrekening(gebruikersnaam, naam, betaalrekening, BEGINSALDO);
        contacten.voegToe(betaalrekening, naam);
        logins.zorgVoorKlant(gebruikersnaam, wachtwoord);
    }
}
