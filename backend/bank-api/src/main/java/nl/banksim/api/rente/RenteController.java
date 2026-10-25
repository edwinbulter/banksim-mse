package nl.banksim.api.rente;

import nl.banksim.api.beveiliging.IngelogdeGebruiker;
import nl.banksim.api.simulatie.SimulationClock;
import nl.banksim.domain.rekening.Iban;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * S8.1 (FO "Spaarrekening scherm"): de lopende rente van een spaarrekening van de ingelogde klant, op "vandaag" voor
 * de bank (ADR 0013). Een rekening van een ander of een onbekende rekening geeft 404 (ADR 0023).
 */
@RestController
class RenteController {

    private final LopendeRente lopendeRente;
    private final IngelogdeGebruiker gebruiker;
    private final SimulationClock klok;

    RenteController(LopendeRente lopendeRente, IngelogdeGebruiker gebruiker, SimulationClock klok) {
        this.lopendeRente = lopendeRente;
        this.gebruiker = gebruiker;
        this.klok = klok;
    }

    @GetMapping("/api/rekeningen/{iban}/lopende-rente")
    @PreAuthorize("isAuthenticated()")
    LopendeRenteJson lopendeRente(@PathVariable String iban) {
        return lopendeRente.van(gebruiker.gebruikersnaam(), ibanOf404(iban), klok.vandaag())
                .map(bedrag -> new LopendeRenteJson(bedrag.toString()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    private static Iban ibanOf404(String tekst) {
        try {
            return Iban.van(tekst);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
    }

    /** ADR 0010: het bedrag als string, bijvoorbeeld "12.36". */
    record LopendeRenteJson(String bedrag) {
    }
}
