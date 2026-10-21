package nl.banksim.api.transactie;

import java.util.List;
import java.util.Optional;

import nl.banksim.domain.rekening.Iban;

interface TransactieRepository {

    /** De transacties van nieuw naar oud; leeg als de rekening niet bestaat of niet van deze gebruiker is. */
    Optional<List<Transactie>> vanRekening(String gebruikersnaam, Iban iban);
}
