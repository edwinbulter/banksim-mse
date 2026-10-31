package nl.banksim.api.probleem;

import java.net.URI;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Backlog fase 3.1: de foutafhandeling op één plek, als probleembericht (RFC 9457). Het domein weigert een ongeldige
 * waarde met een {@link IllegalArgumentException} in klanttaal, bijvoorbeeld een bedrag van € 0,00; dat is een fout van
 * de client, geen fout van de bank.
 */
@RestControllerAdvice
class ProbleemAdvice {

    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail ongeldigeInvoer(IllegalArgumentException fout) {
        ProblemDetail probleem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, fout.getMessage());
        probleem.setType(URI.create("urn:banksim:probleem:ongeldige-invoer"));
        return probleem;
    }
}
