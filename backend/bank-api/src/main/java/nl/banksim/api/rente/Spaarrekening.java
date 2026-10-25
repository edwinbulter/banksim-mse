package nl.banksim.api.rente;

import java.time.LocalDate;
import java.time.YearMonth;

import nl.banksim.domain.rekening.Iban;

/**
 * Een spaarrekening die rente krijgt: vanaf de dag van openen, met de naam van de rekeninghouder. Een seed zonder
 * datum van openen geeft {@code null}: geen rente (ADR 0028).
 */
record Spaarrekening(Iban iban, String naam, LocalDate geopendOp) {

    /** De eerste dag van {@code maand} die rente oplevert: de 1e, of later de dag van openen. */
    LocalDate eersteRentedag(YearMonth maand) {
        return maand.atDay(1).isBefore(geopendOp) ? geopendOp : maand.atDay(1);
    }
}
