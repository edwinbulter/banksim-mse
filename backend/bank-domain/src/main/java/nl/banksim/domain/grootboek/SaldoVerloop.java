package nl.banksim.domain.grootboek;

import java.util.List;

import nl.banksim.domain.geld.Bedrag;

/**
 * Het saldo van een rekening aan het eind van de uitvoerdatum, en daarna de som van de bewegingen per latere datum, in
 * datumvolgorde (ADR 0025).
 */
public record SaldoVerloop(Bedrag saldoOpUitvoerdatum, List<Bedrag> latereBewegingen) {

    public SaldoVerloop {
        latereBewegingen = List.copyOf(latereBewegingen);
    }
}
