package nl.banksim.api.betaling;

import java.net.URI;

import nl.banksim.api.beveiliging.IngelogdeGebruiker;
import nl.banksim.api.grootboek.SaldoOntoereikend;
import nl.banksim.domain.geld.Bedrag;
import nl.banksim.domain.rekening.Iban;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
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
    void betaal(@RequestHeader(name = "Idempotency-Key", required = false) String sleutel, @RequestBody BetalingJson json) {
        if (sleutel == null) {
            throw new SleutelOntbreekt();
        }
        betalen.betaal(gebruiker.gebruikersnaam(), sleutel, Iban.van(json.van()), Iban.van(json.naar()), json.naam(),
                Bedrag.van(json.bedrag()));
    }

    /** RFC 9457: het probleemtype voor de software, de melding voor de klant. */
    @ExceptionHandler(BetalingGeweigerd.class)
    ProblemDetail betalingGeweigerd(BetalingGeweigerd fout) {
        ProblemDetail probleem = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_CONTENT, fout.getMessage());
        probleem.setType(URI.create("urn:banksim:probleem:" + fout.probleem()));
        return probleem;
    }

    @ExceptionHandler(SaldoOntoereikend.class)
    ProblemDetail saldoOntoereikend(SaldoOntoereikend fout) {
        return betalingGeweigerd(new BetalingGeweigerd("saldo-ontoereikend", fout.getMessage()));
    }

    /** ADR 0027: zonder sleutel geen betaling; zo betaalt geen client per ongeluk zonder bescherming. */
    @ExceptionHandler(SleutelOntbreekt.class)
    ProblemDetail sleutelOntbreekt() {
        ProblemDetail probleem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "De header Idempotency-Key ontbreekt.");
        probleem.setType(URI.create("urn:banksim:probleem:sleutel-ontbreekt"));
        return probleem;
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
