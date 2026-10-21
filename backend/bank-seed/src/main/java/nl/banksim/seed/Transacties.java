package nl.banksim.seed;

import java.time.LocalDate;

import nl.banksim.domain.geld.Bedrag;
import nl.banksim.domain.rekening.Iban;

/** De geboekte transacties van een rekening in de database van bank-api; een negatief bedrag is een afschrijving. */
public interface Transacties {

    void boek(Iban iban, LocalDate datum, Bedrag bedrag, String tegenpartij);
}
