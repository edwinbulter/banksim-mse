package nl.banksim.domain.grootboek;

import nl.banksim.domain.geld.Bedrag;
import nl.banksim.domain.rekening.Iban;
import nl.banksim.domain.transactie.TransactieType;

/**
 * Eén kant van een overboeking: het bedrag met teken op de rekening, de andere kant als tegenpartij, en het
 * transactietype zoals deze kant het ziet (ADR 0029).
 */
public record Boeking(Iban rekening, Bedrag bedrag, Partij tegenpartij, TransactieType type) {
}
