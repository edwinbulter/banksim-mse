package nl.banksim.api.rente;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

import nl.banksim.domain.rente.DagSaldo;
import nl.banksim.domain.rekening.Iban;

/** ADR 0028: wat de maandafsluiting uit de database van bank-api nodig heeft. */
interface RenteOpslag {

    /** De spaarrekeningen met een datum van openen. */
    List<Spaarrekening> spaarrekeningen();

    /** Deze spaarrekening als hij van deze gebruiker is (ADR 0023); de datum van openen kan ontbreken. */
    Optional<Spaarrekening> spaarrekening(String gebruikersnaam, Iban iban);

    /** Legt vast dat de rente van deze maand wordt bijgeschreven; false als dat al gebeurd is. */
    boolean markeer(Iban iban, YearMonth maand);

    /** Het eindsaldo van elke dag van `van` tot en met `totEnMet`. */
    List<DagSaldo> dagsaldi(Iban iban, LocalDate van, LocalDate totEnMet);
}
