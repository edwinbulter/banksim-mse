package nl.banksim.seed;

import java.time.LocalDate;
import java.util.Optional;

import nl.banksim.domain.geld.Bedrag;
import nl.banksim.domain.rekening.Iban;

/** S8.2: een spaarrekening voor een klant die al bestaat. */
public interface Spaarrekeningen {

    /** Zonder datum geldt de rekening als vandaag geopend. */
    Iban open(String gebruikersnaam, Bedrag saldo, Optional<LocalDate> geopendOp);
}
