package nl.banksim.seed;

import nl.banksim.domain.geld.Bedrag;

/** Maakt een klant met login en een betaalrekening aan. */
public interface KlantAanmaker {

    GeseedeKlant klantMetBetaalrekening(String alias, Bedrag saldo);
}
