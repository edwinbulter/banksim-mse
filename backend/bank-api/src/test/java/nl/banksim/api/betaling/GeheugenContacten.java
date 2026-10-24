package nl.banksim.api.betaling;

import java.util.HashSet;
import java.util.Set;

import nl.banksim.domain.rekening.Iban;

/** De contactenlijst in het geheugen, voor tests zonder database. */
class GeheugenContacten implements Contacten {

    private final Set<Iban> ibans = new HashSet<>();

    void voegToe(Iban iban) {
        ibans.add(iban);
    }

    void leeg() {
        ibans.clear();
    }

    @Override
    public boolean bevat(Iban iban) {
        return ibans.contains(iban);
    }
}
