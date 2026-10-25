package nl.banksim.seed;

import nl.banksim.domain.geld.Bedrag;
import nl.banksim.domain.rekening.Iban;

/** S8.2: een spaarrekening voor een klant die al bestaat. */
public interface Spaarrekeningen {

    Iban open(String gebruikersnaam, Bedrag saldo);
}
