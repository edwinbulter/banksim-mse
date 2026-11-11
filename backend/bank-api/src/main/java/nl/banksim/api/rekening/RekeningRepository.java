package nl.banksim.api.rekening;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import nl.banksim.domain.rekening.Iban;

interface RekeningRepository {

    /**
     * De rekeningen van de rekeninghouder met deze gebruikersnaam, met het saldo op {@code vandaag} (ADR 0013); leeg
     * als die niet bestaat.
     */
    List<Rekening> vanGebruiker(String gebruikersnaam, LocalDate vandaag);

    /**
     * Een rekening van de rekeninghouder met deze gebruikersnaam, met zijn naam en het saldo op {@code vandaag}; leeg als
     * de rekening niet bestaat of van een ander is (ADR 0023).
     */
    Optional<RekeningMetHouder> vanGebruiker(String gebruikersnaam, Iban iban, LocalDate vandaag);

    /** ADR 0038: voor de beheerder elke betaal- of spaarrekening van een klant; leeg als die niet bestaat. */
    Optional<RekeningMetHouder> inBeheer(Iban iban, LocalDate vandaag);
}
