package nl.banksim.api.betaling;

import java.util.Optional;

/** ADR 0027: de Idempotency-Key van een betaling, per gebruiker, in de databasetransactie van de boeking. */
interface Idempotentie {

    /**
     * Legt de sleutel vast met de hash van het verzoek. Leeg als de sleutel nieuw is; anders de hash van het eerdere
     * verzoek met deze sleutel. Een gelijktijdige poging met dezelfde sleutel wacht tot de eerste klaar is.
     */
    Optional<String> claim(String sleutel, String gebruikersnaam, String verzoekHash);
}
