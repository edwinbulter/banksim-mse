package nl.banksim.api.rente;

import java.time.LocalDate;

import nl.banksim.domain.rekening.Iban;

/** Een spaarrekening die rente krijgt: vanaf de dag van openen, met de naam van de rekeninghouder. */
record Spaarrekening(Iban iban, String naam, LocalDate geopendOp) {
}
