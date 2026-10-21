package nl.banksim.api.transactie;

import java.util.List;

import nl.banksim.api.beveiliging.IngelogdeGebruiker;
import nl.banksim.domain.rekening.Iban;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** De transacties van een rekening van de ingelogde klant, van nieuw naar oud (S2.1). */
@RestController
class TransactieController {

    private final TransactieRepository transacties;
    private final IngelogdeGebruiker gebruiker;

    TransactieController(TransactieRepository transacties, IngelogdeGebruiker gebruiker) {
        this.transacties = transacties;
        this.gebruiker = gebruiker;
    }

    @GetMapping("/api/rekeningen/{iban}/transacties")
    List<TransactieJson> transacties(@PathVariable String iban) {
        return transacties.vanRekening(gebruiker.gebruikersnaam(), ibanOf404(iban))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND))
                .stream().map(TransactieJson::van).toList();
    }

    private static Iban ibanOf404(String tekst) {
        try {
            return Iban.van(tekst);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
    }

    /** ADR 0010: het bedrag als string met teken, bijvoorbeeld "-812.40"; de datum als "2026-10-01". */
    record TransactieJson(String datum, String tegenpartij, String bedrag) {

        static TransactieJson van(Transactie transactie) {
            return new TransactieJson(transactie.datum().toString(), transactie.tegenpartij(), transactie.bedrag().toString());
        }
    }
}
