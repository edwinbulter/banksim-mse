package nl.banksim.api.betaling;

import nl.banksim.api.beveiliging.IngelogdeGebruiker;
import nl.banksim.domain.geld.Bedrag;
import nl.banksim.domain.rekening.Iban;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** S7.1: betalen vanaf de eigen rekening, na "Bevestigen" op het controlescherm. */
@RestController
class BetalingController {

    private final BetaalService betalen;
    private final IngelogdeGebruiker gebruiker;

    BetalingController(BetaalService betalen, IngelogdeGebruiker gebruiker) {
        this.betalen = betalen;
        this.gebruiker = gebruiker;
    }

    @PostMapping("/api/betalingen")
    @PreAuthorize("isAuthenticated()")
    @ResponseStatus(HttpStatus.CREATED)
    void betaal(@RequestBody BetalingJson json) {
        betalen.betaal(gebruiker.gebruikersnaam(), Iban.van(json.van()), Iban.van(json.naar()), json.naam(),
                Bedrag.van(json.bedrag()));
    }

    @ExceptionHandler(RekeningNietGevonden.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    void rekeningNietGevonden() {
        // ADR 0023: dezelfde 404 als voor een rekening die niet bestaat.
    }

    /** ADR 0010: het bedrag als string, bijvoorbeeld "25.00". */
    record BetalingJson(String van, String naar, String naam, String bedrag) {
    }
}
