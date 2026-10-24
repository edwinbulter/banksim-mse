package nl.banksim.domain.grootboek;

import nl.banksim.domain.rekening.Iban;

/** Een kant van een overboeking: de rekening en de naam die de andere kant als tegenpartij ziet. */
public record Partij(Iban iban, String naam) {
}
