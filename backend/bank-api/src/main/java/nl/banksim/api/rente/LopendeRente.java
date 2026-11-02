package nl.banksim.api.rente;

import java.time.LocalDate;
import java.util.Optional;

import nl.banksim.domain.geld.Bedrag;
import nl.banksim.domain.rekening.Iban;

/** S8.1 (FO): de opgebouwde, nog niet bijgeschreven rente van een spaarrekening. */
interface LopendeRente {

    /**
     * Van de 1e van de maand tot en met {@code vandaag}; leeg als dit geen spaarrekening van deze gebruiker is
     * (ADR 0023).
     */
    Optional<Bedrag> van(String gebruikersnaam, Iban iban, LocalDate vandaag);

    /**
     * ADR 0038: als {@link #van}, maar zonder eigenaar, voor de beheerder; leeg als dit geen spaarrekening is. Alleen voor
     * de controller onder /api/beheer, die de rol beheerder vraagt.
     */
    Optional<Bedrag> inBeheer(Iban iban, LocalDate vandaag);
}
