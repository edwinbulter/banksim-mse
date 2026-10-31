package nl.banksim.seed;

import java.util.List;

import nl.banksim.domain.rekening.Iban;

/** De geboekte transacties van een rekening in de database van bank-api; een negatief bedrag is een afschrijving. */
public interface Transacties {

    void boek(Iban iban, Boeking boeking);

    /** Boekt de boekingen in deze volgorde. */
    default void boekAlle(Iban iban, List<Boeking> boekingen) {
        boekingen.forEach(boeking -> boek(iban, boeking));
    }
}
