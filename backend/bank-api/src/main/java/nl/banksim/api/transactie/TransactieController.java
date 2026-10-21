package nl.banksim.api.transactie;

import java.util.List;
import java.util.Optional;

import nl.banksim.api.beveiliging.IngelogdeGebruiker;
import nl.banksim.domain.rekening.Iban;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * De transacties van een rekening van de ingelogde klant, van nieuw naar oud, per {@value #PER_PAGINA} (S2.1, S2.2).
 * "Toon meer" stuurt de cursor uit {@code volgende} terug als {@code na} (ADR 0011).
 */
@RestController
class TransactieController {

    static final int PER_PAGINA = 50;

    private final TransactieRepository transacties;
    private final IngelogdeGebruiker gebruiker;
    private final Cursor cursor;

    TransactieController(TransactieRepository transacties, IngelogdeGebruiker gebruiker, Cursor cursor) {
        this.transacties = transacties;
        this.gebruiker = gebruiker;
        this.cursor = cursor;
    }

    @GetMapping("/api/rekeningen/{iban}/transacties")
    PaginaJson transacties(@PathVariable String iban, @RequestParam Optional<String> na) {
        Iban rekening = ibanOf404(iban);
        Optional<Positie> positie = na.map(tekst -> cursor.lees(rekening, tekst));
        Pagina pagina = transacties.vanRekening(gebruiker.gebruikersnaam(), rekening, positie, PER_PAGINA)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        return new PaginaJson(
                pagina.transacties().stream().map(TransactieJson::van).toList(),
                pagina.volgende().map(volgende -> cursor.maak(rekening, volgende)).orElse(null));
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

    /** ADR 0010: het bedrag als string met teken, bijvoorbeeld "-812.40"; de datum als "2026-10-01". */
    record TransactieJson(String datum, String tegenpartij, String bedrag) {

        static TransactieJson van(Transactie transactie) {
            return new TransactieJson(transactie.datum().toString(), transactie.tegenpartij(), transactie.bedrag().toString());
        }
    }
}
