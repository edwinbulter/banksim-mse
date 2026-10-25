package nl.banksim.domain.rente;

import java.time.LocalDate;

import nl.banksim.domain.geld.Bedrag;

/** Het eindsaldo van een rekening aan het eind van een dag. */
public record DagSaldo(LocalDate dag, Bedrag eindsaldo) {
}
