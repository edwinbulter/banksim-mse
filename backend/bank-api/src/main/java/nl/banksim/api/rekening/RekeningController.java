package nl.banksim.api.rekening;

import java.util.List;

import nl.banksim.api.beveiliging.IngelogdeGebruiker;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Het overzicht: de rekeningen van de ingelogde klant (S1.1). */
@RestController
class RekeningController {

    private final RekeningRepository rekeningen;
    private final IngelogdeGebruiker gebruiker;

    RekeningController(RekeningRepository rekeningen, IngelogdeGebruiker gebruiker) {
        this.rekeningen = rekeningen;
        this.gebruiker = gebruiker;
    }

    @GetMapping("/api/rekeningen")
    List<RekeningJson> rekeningen() {
        return rekeningen.vanGebruiker(gebruiker.gebruikersnaam()).stream().map(RekeningJson::van).toList();
    }

    /** ADR 0010: het saldo als string, bijvoorbeeld "1842.17". */
    record RekeningJson(String soort, String iban, String saldo) {

        static RekeningJson van(Rekening rekening) {
            return new RekeningJson(rekening.soort().code(), rekening.iban().toString(), rekening.saldo().toString());
        }
    }
}
