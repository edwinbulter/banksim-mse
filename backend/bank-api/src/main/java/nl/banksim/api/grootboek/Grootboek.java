package nl.banksim.api.grootboek;

import java.util.Optional;

import nl.banksim.domain.grootboek.Overboeking;
import nl.banksim.domain.rekening.Iban;

/** De rekeningen en overboekingen in de database van bank-api (ADR 0024). Alleen {@link LedgerService} schrijft. */
public interface Grootboek {

    /** De naam van de rekeninghouder, als de rekening van deze gebruiker is (ADR 0023). */
    Optional<String> naamBijEigenRekening(String gebruikersnaam, Iban iban);

    /** De overboeking met haar twee transacties, in één databasetransactie. */
    void schrijf(Overboeking overboeking);
}
