package nl.banksim.api.rekening;

import java.util.List;

import nl.banksim.api.beveiliging.IngelogdeGebruiker;
import nl.banksim.api.simulatie.SimulationClock;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Het overzicht: de rekeningen van de ingelogde klant (S1.1), met het saldo op "vandaag" voor de bank (ADR 0013). */
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

    /** ADR 0010: het saldo als string, bijvoorbeeld "1842.17". */
    record RekeningJson(String soort, String iban, String saldo) {

        static RekeningJson van(Rekening rekening) {
            return new RekeningJson(rekening.soort().code(), rekening.iban().toString(), rekening.saldo().toString());
        }
    }
}
