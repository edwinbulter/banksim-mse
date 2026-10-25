package nl.banksim.api.betaling;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import nl.banksim.domain.grootboek.Contact;
import nl.banksim.domain.rekening.Iban;

/** De contactenlijst in het geheugen, voor tests zonder database. */
class GeheugenContacten implements Contacten {

    private final Map<Iban, Contact> contacten = new HashMap<>();
    private final List<String> gezocht = new ArrayList<>();

    void voegToe(Contact contact) {
        contacten.put(contact.iban(), contact);
    }

    void leeg() {
        contacten.clear();
        gezocht.clear();
    }

    /** Per zoekvraag "gebruikersnaam tekst". */
    List<String> gezocht() {
        return List.copyOf(gezocht);
    }

    /** Geeft alle contacten: de zoekregels zelf test JdbcContactenTests. */
    @Override
    public List<Contact> zoek(String tekst, String gebruikersnaam) {
        gezocht.add(gebruikersnaam + " " + tekst);
        return List.copyOf(contacten.values());
    }

    @Override
    public Optional<Contact> vind(Iban iban) {
        return Optional.ofNullable(contacten.get(iban));
    }
}
