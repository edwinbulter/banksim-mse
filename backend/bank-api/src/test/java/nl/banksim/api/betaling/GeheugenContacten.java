package nl.banksim.api.betaling;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import nl.banksim.domain.grootboek.Contact;
import nl.banksim.domain.rekening.Iban;

/** De contactenlijst in het geheugen, voor tests zonder database. */
class GeheugenContacten implements Contacten {

    private final Map<Iban, Contact> contacten = new HashMap<>();

    void voegToe(Contact contact) {
        contacten.put(contact.iban(), contact);
    }

    void leeg() {
        contacten.clear();
    }

    @Override
    public Optional<Contact> vind(Iban iban) {
        return Optional.ofNullable(contacten.get(iban));
    }
}
