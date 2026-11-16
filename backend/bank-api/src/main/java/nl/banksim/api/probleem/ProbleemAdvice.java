package nl.banksim.api.probleem;

import java.net.URI;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Backlog fase 3.1: de foutafhandeling op één plek, als probleembericht (RFC 9457). Het domein weigert een ongeldige
 * waarde met een {@link IllegalArgumentException} in klanttaal, bijvoorbeeld een bedrag van € 0,00; dat is een fout van
 * de client, geen fout van de bank.
 *
 * <p>ADR 0054: een onverwachte fout wordt een 500 met een algemene melding; de fout zelf staat in de log, bij het
 * trace-ID dat ook in de melding staat. Dit advies komt na de adviezen van de features, die hun eigen fouten afhandelen.
 */
@RestControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE)
class ProbleemAdvice {

    private static final Logger LOG = LoggerFactory.getLogger(ProbleemAdvice.class);

    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail ongeldigeInvoer(IllegalArgumentException fout) {
        ProblemDetail probleem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, fout.getMessage());
        probleem.setType(URI.create("urn:banksim:probleem:ongeldige-invoer"));
        return probleem;
    }

    /**
     * Een fout van Spring zelf gaat terug naar de eigen afhandeling van Spring: een 405, een 400 bij een parameter die
     * niet te lezen is, en de 401 en 403 van Spring Security. Spring geeft die fout dan door aan de volgende resolver.
     */
    @ExceptionHandler(Exception.class)
    ProblemDetail onverwachteFout(Exception fout) throws Exception {
        if (fout instanceof ErrorResponse || fout.getClass().getPackageName().startsWith("org.springframework.")) {
            throw fout;
        }
        LOG.error("Onverwachte fout", fout);
        ProblemDetail probleem = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR,
                "Er ging iets mis bij de bank. Probeer het later opnieuw.");
        probleem.setType(URI.create("urn:banksim:probleem:onverwachte-fout"));
        return probleem;
    }
}
