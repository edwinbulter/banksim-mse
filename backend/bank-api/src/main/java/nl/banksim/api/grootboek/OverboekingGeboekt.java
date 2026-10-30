package nl.banksim.api.grootboek;

import nl.banksim.domain.grootboek.Overboeking;

/**
 * ADR 0030: {@link LedgerService} heeft deze overboeking geschreven. Een listener draait synchroon, in dezelfde
 * databasetransactie: wordt de boeking teruggedraaid, dan ook wat de listener boekt. Zo boekt de rente-code een
 * rentecorrectie zonder dat het grootboek de rente-code kent.
 */
public record OverboekingGeboekt(Overboeking overboeking) {
}
