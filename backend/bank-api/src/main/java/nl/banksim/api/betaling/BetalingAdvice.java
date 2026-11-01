package nl.banksim.api.betaling;

import java.net.URI;

import nl.banksim.api.grootboek.SaldoOntoereikend;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** De weigeringen van betalen en overschrijven, op één plek (backlog fase 3.1). */
@RestControllerAdvice(assignableTypes = {BetalingController.class, OverschrijvingController.class})
class BetalingAdvice {

    /** RFC 9457: het probleemtype voor de software, de melding voor de klant. */
    @ExceptionHandler(BetalingGeweigerd.class)
    ProblemDetail geweigerd(BetalingGeweigerd fout) {
        ProblemDetail probleem = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_CONTENT, fout.getMessage());
        probleem.setType(URI.create("urn:banksim:probleem:" + fout.probleem().code()));
        return probleem;
    }

    /** ADR 0025: geen rekening komt onder € 0,00, ook een spaarrekening niet. */
    @ExceptionHandler(SaldoOntoereikend.class)
    ProblemDetail saldoOntoereikend(SaldoOntoereikend fout) {
        return geweigerd(new BetalingGeweigerd(Weigering.SALDO_ONTOEREIKEND, fout.getMessage()));
    }

    /** ADR 0027: zonder sleutel geen betaling of overschrijving; zo boekt geen client per ongeluk zonder bescherming. */
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
}
