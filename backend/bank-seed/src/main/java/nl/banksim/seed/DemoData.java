package nl.banksim.seed;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

import nl.banksim.domain.grootboek.Overboeking;
import nl.banksim.domain.grootboek.Partij;
import nl.banksim.domain.rekening.Iban;
import nl.banksim.domain.transactie.TransactieType;
import org.springframework.transaction.support.TransactionOperations;

/**
 * ADR 0040, S11.1: de 10 klanten en de beheerder van de demo; een tweede keer verandert er niets. De klanten ontstaan samen in één
 * databasetransactie (ADR 0044): per klant de rekeninghouder, zijn twee rekeningen, zijn transacties (ADR 0041), zijn inleggen (ADR 0042) en de login,
 * en daarna hun betalingen aan elkaar. Mislukt er iets, dan volgt alles bij de volgende installatie opnieuw.
 */
class DemoData {

    /** De renterekening van BankSim (V11); de spaarrekening ziet "BankSim" als tegenpartij (ADR 0043). */
    private static final Partij BANKSIM = new Partij(Iban.van("NL24SIMB0000000000"), "BankSim");

    private final DemoLogins logins;
    private final BestaandeKlanten bestaand;
    private final Rekeninghouders rekeninghouders;
    private final Spaarrekeningen spaarrekeningen;
    private final Transacties transacties;
    private final Overboekingen overboekingen;
    private final Rentebijschrijvingen rentebijschrijvingen;
    private final Supplier<Iban> nieuweIban;
    private final TransactionOperations transactie;
    private final String klantWachtwoord;
    private final String beheerderWachtwoord;

    DemoData(DemoLogins logins, BestaandeKlanten bestaand, Rekeninghouders rekeninghouders, Spaarrekeningen spaarrekeningen,
            Transacties transacties, Overboekingen overboekingen, Rentebijschrijvingen rentebijschrijvingen, Supplier<Iban> nieuweIban, TransactionOperations transactie, String klantWachtwoord, String beheerderWachtwoord) {
        this.logins = logins;
        this.bestaand = bestaand;
        this.rekeninghouders = rekeninghouders;
        this.spaarrekeningen = spaarrekeningen;
        this.transacties = transacties;
        this.overboekingen = overboekingen;
        this.rentebijschrijvingen = rentebijschrijvingen;
        this.nieuweIban = nieuweIban;
        this.transactie = transactie;
        this.klantWachtwoord = klantWachtwoord;
        this.beheerderWachtwoord = beheerderWachtwoord;
    }

    void maak() {
        if (leeg(klantWachtwoord) || leeg(beheerderWachtwoord)) {
            throw new IllegalStateException("De wachtwoorden uit het Secret bank-demo ontbreken");
        }
        if (DemoKlanten.ALLE.stream().noneMatch(klant -> bestaand.bestaat(klant.gebruikersnaam()))) {
            transactie.executeWithoutResult(status -> maakKlanten());
        }
        logins.zorgVoorBeheerder(DemoKlanten.BEHEERDER, beheerderWachtwoord);
    }

    private void maakKlanten() {
        Map<String, Partij> betaalrekeningen = new HashMap<>();
        DemoKlanten.ALLE.forEach(klant -> betaalrekeningen.put(klant.gebruikersnaam(), maak(klant)));
        overboekingen.schrijfAlle(Generator.betalingenTussenKlanten(DemoKlanten.ALLE).stream()
                .map(betaling -> new Overboeking(betaalrekeningen.get(betaling.van()), betaalrekeningen.get(betaling.naar()),
                        betaling.bedrag(), TransactieType.ONLINE_BANKIEREN, betaling.tijdstip()))
                .toList());
    }

    /** Met de rente tot en met de laatste maand: de maandafsluiting slaat die maanden daarna over (ADR 0043). */
    private Partij maak(DemoKlanten.DemoKlant klant) {
        Iban betaalrekening = nieuweIban.get();
        rekeninghouders.voegToeMetBetaalrekening(klant.gebruikersnaam(), klant.naam(), betaalrekening, klant.beginsaldo());
        Iban spaarrekening = spaarrekeningen.open(klant.gebruikersnaam(), klant.beginsaldoSpaar(), Optional.of(Generator.GEOPEND_OP));
        transacties.boekAlle(betaalrekening, Generator.boekingen(klant));
        Partij betaal = new Partij(betaalrekening, klant.naam());
        Partij spaar = new Partij(spaarrekening, klant.naam());
        overboekingen.schrijfAlle(Stream.of(
                Generator.inleggen(klant).stream()
                        .map(inleg -> Overboeking.inleg(betaal, spaar, inleg.bedrag(), inleg.tijdstip(), "Maandelijks sparen")),
                Generator.opnames(klant).stream()
                        .map(opname -> Overboeking.opname(spaar, betaal, opname.bedrag(), opname.tijdstip(), "Aanvulling betaalrekening")),
                Generator.rente(klant).stream()
                        .map(rente -> new Overboeking(BANKSIM, spaar, rente.bedrag(), TransactieType.RENTE, rente.tijdstip(),
                                rente.omschrijving(), null, null)))
                .flatMap(Function.identity())
                .toList());
        rentebijschrijvingen.legVast(spaarrekening, Generator.rente(klant).stream().map(Generator.Rente::maand).toList());
        logins.zorgVoorKlant(klant.gebruikersnaam(), klantWachtwoord);
        return betaal;
    }

    private static boolean leeg(String wachtwoord) {
        return wachtwoord == null || wachtwoord.isBlank();
    }
}
