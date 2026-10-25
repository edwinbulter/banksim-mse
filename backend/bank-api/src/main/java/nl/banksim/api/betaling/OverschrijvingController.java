package nl.banksim.api.betaling;

import java.net.URI;

import nl.banksim.api.beveiliging.IngelogdeGebruiker;
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

/** S9.1 (FO "Overschrijven scherm"): geld tussen de eigen betaal- en spaarrekening. */
@RestController
class OverschrijvingController {

    private final OverschrijfService overschrijven;
    private final IngelogdeGebruiker gebruiker;

    OverschrijvingController(OverschrijfService overschrijven, IngelogdeGebruiker gebruiker) {
        this.overschrijven = overschrijven;
        this.gebruiker = gebruiker;
    }

    @PostMapping("/api/overschrijvingen")
    @PreAuthorize("isAuthenticated()")
    @ResponseStatus(HttpStatus.CREATED)
    void schrijfOver(@RequestHeader(name = "Idempotency-Key", required = false) String sleutel,
            @RequestBody OverschrijvingJson json) {
        if (sleutel == null) {
            throw new SleutelOntbreekt();
        }
        overschrijven.schrijfOver(gebruiker.gebruikersnaam(), sleutel, Iban.van(json.van()), Iban.van(json.naar()),
                Bedrag.van(json.bedrag()));
    }

    /** RFC 9457, zoals bij betalen. */
    @ExceptionHandler(BetalingGeweigerd.class)
    ProblemDetail geweigerd(BetalingGeweigerd fout) {
        ProblemDetail probleem = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_CONTENT, fout.getMessage());
        probleem.setType(URI.create("urn:banksim:probleem:" + fout.probleem()));
        return probleem;
    }

    /** ADR 0027: zonder sleutel geen overschrijving. */
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

    /** ADR 0010: het bedrag als string, bijvoorbeeld "250.00". */
    record OverschrijvingJson(String van, String naar, String bedrag) {
    }
}
