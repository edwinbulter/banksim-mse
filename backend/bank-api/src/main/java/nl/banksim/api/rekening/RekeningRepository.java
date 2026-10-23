package nl.banksim.api.rekening;

import java.time.LocalDate;
import java.util.List;

interface RekeningRepository {

    /**
     * De rekeningen van de rekeninghouder met deze gebruikersnaam, met het saldo op {@code vandaag} (ADR 0013); leeg
     * als die niet bestaat.
     */
    List<Rekening> vanGebruiker(String gebruikersnaam, LocalDate vandaag);
}
