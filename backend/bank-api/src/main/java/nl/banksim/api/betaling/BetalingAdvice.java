package nl.banksim.api.betaling;

import java.net.URI;

import jakarta.servlet.http.HttpServletRequest;
import nl.banksim.api.grootboek.SaldoOntoereikend;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** De weigeringen van betalen en overschrijven, op één plek (backlog fase 3.1). */
@RestControllerAdvice(assignableTypes = {BetalingController.class, OverschrijvingController.class})
class BetalingAdvice {

    private static final Logger LOG = LoggerFactory.getLogger(BetalingAdvice.class);

    /** RFC 9457: het probleemtype voor de software, de melding voor de klant. */
    @ExceptionHandler(BetalingGeweigerd.class)
    ProblemDetail geweigerd(BetalingGeweigerd fout, HttpServletRequest verzoek) {
        // ADR 0054: alleen het probleemtype; de melding kan een naam bevatten.
        LOG.info("Betaling geweigerd: {}", fout.probleem().code());
        Boekingsmetrics.geweigerd(fout.probleem(), Boekingsmetrics.soortVan(verzoek.getRequestURI()));
        ProblemDetail probleem = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_CONTENT, fout.getMessage());
        probleem.setType(URI.create("urn:banksim:probleem:" + fout.probleem().code()));
        return probleem;
    }

    /** ADR 0025: geen rekening komt onder € 0,00, ook een spaarrekening niet. */
    @ExceptionHandler(SaldoOntoereikend.class)
    ProblemDetail saldoOntoereikend(SaldoOntoereikend fout, HttpServletRequest verzoek) {
        return geweigerd(new BetalingGeweigerd(Weigering.SALDO_ONTOEREIKEND, fout.getMessage()), verzoek);
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
