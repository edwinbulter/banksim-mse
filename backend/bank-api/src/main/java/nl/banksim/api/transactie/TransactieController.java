package nl.banksim.api.transactie;

import java.time.format.DateTimeFormatter;
import java.net.URI;
import java.util.List;
import java.util.Optional;

import nl.banksim.api.beveiliging.IngelogdeGebruiker;
import nl.banksim.api.simulatie.SimulationClock;
import nl.banksim.domain.rekening.Iban;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * De transacties van een rekening van de ingelogde klant tot en met "vandaag" voor de bank (ADR 0013), van nieuw naar
 * oud, per {@value #PER_PAGINA} (S2.1, S2.2).
 * "Toon meer" stuurt de cursor uit {@code volgende} terug als {@code na} (ADR 0011).
 */
@RestController
class TransactieController {

    static final int PER_PAGINA = 50;

    private final TransactieRepository transacties;
    private final IngelogdeGebruiker gebruiker;
    private final Cursor cursor;
    private final SimulationClock klok;

    TransactieController(TransactieRepository transacties, IngelogdeGebruiker gebruiker, Cursor cursor,
            SimulationClock klok) {
        this.transacties = transacties;
        this.gebruiker = gebruiker;
        this.cursor = cursor;
        this.klok = klok;
    }

    /** S5.1: de zoekvelden zijn optioneel; "Toon meer" stuurt ze opnieuw mee, naast {@code na}. */
    @GetMapping("/api/rekeningen/{iban}/transacties")
    @PreAuthorize("isAuthenticated()")
    PaginaJson transacties(@PathVariable String iban, @RequestParam Optional<String> na,
            @RequestParam Optional<String> tekst, @RequestParam Optional<String> van, @RequestParam Optional<String> tm,
            @RequestParam Optional<String> type, @RequestParam Optional<String> richting) {
        Iban rekening = ibanOf404(iban);
        Zoekfilter filter = Zoekfilter.van(tekst, van, tm, type, richting);
        Optional<Positie> positie = na.map(cursortekst -> cursor.lees(rekening, cursortekst));
        Pagina pagina = transacties.vanRekening(gebruiker.gebruikersnaam(), rekening, klok.vandaag(), filter, positie, PER_PAGINA)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        return new PaginaJson(
                pagina.transacties().stream().map(TransactieJson::van).toList(),
                pagina.volgende().map(volgende -> cursor.maak(rekening, volgende)).orElse(null));
    }

    /** RFC 9457: het probleemtype voor de software, de melding voor de klant. */
    @ExceptionHandler(OngeldigZoekfilter.class)
    ProblemDetail ongeldigZoekfilter(OngeldigZoekfilter fout) {
        ProblemDetail probleem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, fout.getMessage());
        probleem.setType(URI.create("urn:banksim:probleem:" + fout.probleem()));
        return probleem;
    }

    @ExceptionHandler(OngeldigeCursor.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    void ongeldigeCursor() {
        // 400 zonder body; de klant opent de rekening opnieuw.
    }

    private static Iban ibanOf404(String tekst) {
        try {
            return Iban.van(tekst);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
    }

    /** {@code volgende} is null aan het eind van de lijst. */
    record PaginaJson(List<TransactieJson> transacties, String volgende) {
    }

    /**
     * ADR 0010: het bedrag als string met teken, bijvoorbeeld "-812.40"; de datum als "2026-10-01". S4.1: het type, het
     * tijdstip met offset in Europe/Amsterdam, en Van en Naar met naam en IBAN (null zonder IBAN).
     */
    record TransactieJson(String datum, String tegenpartij, String bedrag, String type, String tijdstip, PartijJson van,
            PartijJson naar) {

        static TransactieJson van(Transactie transactie) {
            return new TransactieJson(transactie.datum().toString(), transactie.tegenpartij(), transactie.bedrag().toString(),
                    transactie.type().name(),
                    transactie.tijdstip().atZoneSameInstant(SimulationClock.BANK).toOffsetDateTime().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                    PartijJson.van(transactie.van()), PartijJson.van(transactie.naar()));
        }
    }

    record PartijJson(String naam, String iban) {

        static PartijJson van(Partij partij) {
            return new PartijJson(partij.naam(), partij.iban().map(Iban::toString).orElse(null));
        }
    }
}
