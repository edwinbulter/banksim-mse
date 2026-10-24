package nl.banksim.api.grootboek;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import nl.banksim.domain.geld.Bedrag;
import nl.banksim.domain.grootboek.Overboeking;
import nl.banksim.domain.rekening.Iban;

/** Het grootboek in het geheugen, voor tests zonder database: rekeningen per gebruiker en de geschreven overboekingen. */
public class GeheugenGrootboek implements Grootboek {

    private final Map<Iban, String> gebruikers = new HashMap<>();
    private final Map<Iban, String> namen = new HashMap<>();
    private final Map<Iban, Bedrag> saldi = new HashMap<>();
    private final List<Overboeking> geschreven = new ArrayList<>();
    private final List<String> saldoGevraagd = new ArrayList<>();

    /** Een rekening met een vast saldo; geschreven overboekingen veranderen het niet. */
    public void rekening(String gebruikersnaam, Iban iban, String naam, Bedrag saldo) {
        gebruikers.put(iban, gebruikersnaam);
        namen.put(iban, naam);
        saldi.put(iban, saldo);
    }

    public void leeg() {
        gebruikers.clear();
        namen.clear();
        saldi.clear();
        geschreven.clear();
        saldoGevraagd.clear();
    }

    public List<Overboeking> geschreven() {
        return List.copyOf(geschreven);
    }

    @Override
    public Optional<String> naamBijEigenRekening(String gebruikersnaam, Iban iban) {
        return gebruikersnaam.equals(gebruikers.get(iban)) ? Optional.of(namen.get(iban)) : Optional.empty();
    }

    /** Per vraag "IBAN datum". */
    public List<String> saldoGevraagd() {
        return List.copyOf(saldoGevraagd);
    }

    @Override
    public Bedrag saldo(Iban iban, LocalDate datum) {
        saldoGevraagd.add(iban + " " + datum);
        return saldi.get(iban);
    }

    @Override
    public void schrijf(Overboeking overboeking) {
        geschreven.add(overboeking);
    }
}
