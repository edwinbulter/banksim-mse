package nl.banksim.api.grootboek;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import nl.banksim.domain.grootboek.Overboeking;
import nl.banksim.domain.rekening.Iban;

/** Het grootboek in het geheugen, voor tests zonder database: rekeningen per gebruiker en de geschreven overboekingen. */
public class GeheugenGrootboek implements Grootboek {

    private final Map<Iban, String> gebruikers = new HashMap<>();
    private final Map<Iban, String> namen = new HashMap<>();
    private final List<Overboeking> geschreven = new ArrayList<>();

    public void rekening(String gebruikersnaam, Iban iban, String naam) {
        gebruikers.put(iban, gebruikersnaam);
        namen.put(iban, naam);
    }

    public void leeg() {
        gebruikers.clear();
        namen.clear();
        geschreven.clear();
    }

    public List<Overboeking> geschreven() {
        return List.copyOf(geschreven);
    }

    @Override
    public Optional<String> naamBijEigenRekening(String gebruikersnaam, Iban iban) {
        return gebruikersnaam.equals(gebruikers.get(iban)) ? Optional.of(namen.get(iban)) : Optional.empty();
    }

    @Override
    public void schrijf(Overboeking overboeking) {
        geschreven.add(overboeking);
    }
}
