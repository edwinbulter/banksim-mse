package nl.banksim.api.betaling;

import nl.banksim.domain.geld.Bedrag;
import nl.banksim.domain.rekening.Iban;

/**
 * Wat de klant op het controlescherm bevestigt (S7.1). Omschrijving, betalingskenmerk en extra omschrijving zijn null als
 * ze er niet zijn (FO), net als in {@code Overboeking}.
 */
record Betaalopdracht(Iban van, Iban naar, String naam, Bedrag bedrag, String omschrijving, String betalingskenmerk,
        String extraOmschrijving) {
}
