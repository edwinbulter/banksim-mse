package nl.banksim.api.transactie;

import java.util.Optional;

import nl.banksim.domain.rekening.Iban;

interface TransactieRepository {

    /**
     * Hoogstens {@code aantal} transacties van nieuw naar oud, na {@code na} (of vanaf de nieuwste); leeg als de
     * rekening niet bestaat of niet van deze gebruiker is.
     */
    Optional<Pagina> vanRekening(String gebruikersnaam, Iban iban, Optional<Positie> na, int aantal);
}
