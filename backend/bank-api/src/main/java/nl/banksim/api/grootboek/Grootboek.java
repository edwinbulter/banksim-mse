package nl.banksim.api.grootboek;

import java.time.LocalDate;
import java.util.Optional;

import nl.banksim.domain.grootboek.Overboeking;
import nl.banksim.domain.grootboek.SaldoVerloop;
import nl.banksim.domain.rekening.Iban;

/** De rekeningen en overboekingen in de database van bank-api (ADR 0024). Alleen {@link LedgerService} schrijft. */
public interface Grootboek {

    /** De naam van de rekeninghouder, als de rekening van deze gebruiker is (ADR 0023). */
    Optional<String> naamBijEigenRekening(String gebruikersnaam, Iban iban);

    /**
     * Het saldo aan het eind van de datum (openingssaldo plus de transacties tot en met die datum), en de som van de
     * bewegingen per latere datum, in datumvolgorde (ADR 0025).
     */
    SaldoVerloop saldoVerloop(Iban iban, LocalDate datum);

    /** De overboeking met haar twee transacties, in één databasetransactie. */
    void schrijf(Overboeking overboeking);
}
