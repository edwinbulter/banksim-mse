package nl.banksim.seed;

import nl.banksim.domain.rekening.Iban;

/** De geboekte transacties van een rekening in de database van bank-api; een negatief bedrag is een afschrijving. */
public interface Transacties {

    void boek(Iban iban, Boeking boeking);
}
