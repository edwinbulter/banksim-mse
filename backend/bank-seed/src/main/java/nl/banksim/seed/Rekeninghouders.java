package nl.banksim.seed;

import nl.banksim.domain.geld.Bedrag;
import nl.banksim.domain.rekening.Iban;

/** De rekeninghouders en rekeningen in de database van bank-api (ADR 0009). */
public interface Rekeninghouders {

    void voegToeMetBetaalrekening(String gebruikersnaam, Iban iban, Bedrag openingssaldo);
}
