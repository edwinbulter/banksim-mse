package nl.banksim.domain.grootboek;

import nl.banksim.domain.geld.Bedrag;
import nl.banksim.domain.rekening.Iban;

/** Eén kant van een overboeking: het bedrag met teken op de rekening, en de andere kant als tegenpartij. */
public record Boeking(Iban rekening, Bedrag bedrag, Partij tegenpartij) {
}
