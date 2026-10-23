package nl.banksim.api.transactie;

import java.time.LocalDate;
import java.util.Optional;

import nl.banksim.domain.rekening.Iban;

interface TransactieRepository {

    /**
     * Hoogstens {@code aantal} transacties tot en met {@code vandaag} (ADR 0013), van nieuw naar oud, na {@code na} (of
     * vanaf de nieuwste); leeg als de rekening niet bestaat of niet van deze gebruiker is.
     */
    Optional<Pagina> vanRekening(String gebruikersnaam, Iban iban, LocalDate vandaag, Zoekfilter filter, Optional<Positie> na,
            int aantal);
}
