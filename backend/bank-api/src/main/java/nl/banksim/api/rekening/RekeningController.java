package nl.banksim.api.rekening;

import java.util.List;

import nl.banksim.api.beveiliging.IngelogdeGebruiker;
import nl.banksim.api.simulatie.SimulationClock;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Het overzicht: de rekeningen van de ingelogde klant (S1.1), met het saldo op "vandaag" voor de bank (ADR 0013). Eén
 * rekening met de naam van de rekeninghouder is de kop van het rekeningscherm (FO "Kopgedeelte"); een rekening van een
 * ander of een onbekende rekening geeft 404 (ADR 0023).
 */
@RestController
class RekeningController {

    private final RekeningRepository rekeningen;
    private final IngelogdeGebruiker gebruiker;
    private final SimulationClock klok;

    RekeningController(RekeningRepository rekeningen, IngelogdeGebruiker gebruiker, SimulationClock klok) {
        this.rekeningen = rekeningen;
        this.gebruiker = gebruiker;
        this.klok = klok;
    }

    @GetMapping("/api/rekeningen")
    @PreAuthorize("isAuthenticated()")
    List<RekeningJson> rekeningen() {
        return rekeningen.vanGebruiker(gebruiker.gebruikersnaam(), klok.vandaag()).stream().map(RekeningJson::van).toList();
    }

    @GetMapping("/api/rekeningen/{iban}")
    @PreAuthorize("isAuthenticated()")
    RekeningMetHouderJson rekening(@PathVariable String iban) {
        return rekeningen.vanGebruiker(gebruiker.gebruikersnaam(), IbanInPad.van(iban), klok.vandaag())
                .map(RekeningMetHouderJson::van)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    /** ADR 0038: dezelfde kop voor de beheerder, van elke rekening van een klant, alleen-lezen. */
    @GetMapping("/api/beheer/rekeningen/{iban}")
    @PreAuthorize("hasRole('beheerder')")
    RekeningMetHouderJson rekeningInBeheer(@PathVariable String iban) {
        return rekeningen.inBeheer(IbanInPad.van(iban), klok.vandaag())
                .map(RekeningMetHouderJson::van)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    /** ADR 0010: het saldo als string, bijvoorbeeld "1842.17". */
    record RekeningJson(String soort, String iban, String saldo) {

        static RekeningJson van(Rekening rekening) {
            return new RekeningJson(rekening.soort().code(), rekening.iban().toString(), rekening.saldo().toString());
        }
    }

    record RekeningMetHouderJson(String soort, String iban, String saldo, String rekeninghouder) {

        static RekeningMetHouderJson van(RekeningMetHouder rekening) {
            return new RekeningMetHouderJson(rekening.soort().code(), rekening.iban().toString(), rekening.saldo().toString(),
                    rekening.rekeninghouder());
        }
    }
}
