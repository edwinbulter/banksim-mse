package nl.banksim.api.rente;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

import nl.banksim.domain.geld.Bedrag;
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

    /** ADR 0030: of de rente van deze maand al is bijgeschreven. */
    boolean isBijgeschreven(Iban iban, YearMonth maand);

    /** ADR 0030: de rente en de correcties die voor deze maand al op de spaarrekening zijn geboekt. */
    Bedrag geboekteRente(Iban iban, YearMonth maand);

    /**
     * Het eindsaldo van elke dag van `van` tot en met `totEnMet`, zonder de rente die in die periode is geboekt: de rente
     * van een maand telt niet mee in haar eigen berekening (ADR 0030). Rente van eerdere maanden telt wel mee.
     */
    List<DagSaldo> dagsaldi(Iban iban, LocalDate van, LocalDate totEnMet);
}
